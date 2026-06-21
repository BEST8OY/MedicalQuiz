package com.medqb.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.CacheManager
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.domain.AppIntentSink
import com.medqb.app.shared.domain.LoadQuestionUseCase
import com.medqb.app.shared.domain.SnackbarSink
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.ui.state.QuizUiState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_SCROLL_CACHE_SIZE = 100

/**
 * Scoped ViewModel for the active Quiz session.
 * Manages question selection, answer submission, log updates, scroll caching, and text highlights.
 * Filter selections are read from the shared FilterStateHolder.
 */
@Inject
class QuizViewModel(
    internal val settingsRepository: SettingsRepository,
    private val textHighlightsRepository: TextHighlightsRepository,
    private val sessionRepository: QuizSessionRepository,
    private val cacheManager: CacheManager,
    private val savedStateHandle: SavedStateHandle,
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val applyFiltersUseCase: ApplyFiltersUseCase,
    private val loadQuestionUseCase: LoadQuestionUseCase,
    private val appIntentSink: AppIntentSink,
    private val snackbarSink: SnackbarSink,
    private val filterStateHolder: FilterStateHolder,
) : ViewModel() {

    private companion object {
        const val KEY_DATABASE_NAME = "database_name"
        const val KEY_CURRENT_QUESTION_INDEX = "current_question_index"
        const val KEY_IS_LOGGING_ENABLED = "is_logging_enabled"
        const val KEY_SUBMISSION_MODE = "submission_mode"
    }

    private var sessionId = ""

    private val _state = MutableStateFlow(QuizUiState.EMPTY)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    val toolbarTitle = state
        .map { it.databaseName }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ""
        )



    private val scrollPositionCache = object : LinkedHashMap<Long, Int>(MAX_SCROLL_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Int>?): Boolean {
            return size > MAX_SCROLL_CACHE_SIZE
        }
    }

    init {
        restoreFromSavedState()
        observeSettings(settingsRepository)

        // Observe active database name changes
        viewModelScope.launch {
            activeDatabaseHolder.databaseName.collect { dbName ->
                if (dbName.isNotEmpty()) {
                    _state.update { it.copy(databaseName = dbName) }
                }
            }
        }
    }

    private fun restoreFromSavedState() {
        val savedDatabaseName = savedStateHandle.get<String>(KEY_DATABASE_NAME).orEmpty()
        val savedQuestionIndex = savedStateHandle.get<Int>(KEY_CURRENT_QUESTION_INDEX) ?: 0
        val savedIsLoggingEnabled = savedStateHandle.get<Boolean>(KEY_IS_LOGGING_ENABLED)
            ?: settingsRepository.isLoggingEnabled.value
        val savedSubmissionModeName = savedStateHandle.get<String>(KEY_SUBMISSION_MODE)
        val savedSubmissionMode = savedSubmissionModeName
            ?.let { runCatching { SubmissionMode.valueOf(it) }.getOrNull() }
            ?: settingsRepository.submissionMode.value

        _state.update {
            it.copy(
                databaseName = savedDatabaseName,
                currentQuestionIndex = savedQuestionIndex.coerceAtLeast(0),
                isLoggingEnabled = savedIsLoggingEnabled,
                submissionMode = savedSubmissionMode,
            )
        }
    }

    private fun persistStateSnapshot(snapshot: QuizUiState = state.value) {
        savedStateHandle[KEY_DATABASE_NAME] = snapshot.databaseName
        savedStateHandle[KEY_CURRENT_QUESTION_INDEX] = snapshot.currentQuestionIndex
        savedStateHandle[KEY_IS_LOGGING_ENABLED] = snapshot.isLoggingEnabled
        savedStateHandle[KEY_SUBMISSION_MODE] = snapshot.submissionMode.name
    }

    fun getTextHighlightsRepository(): TextHighlightsRepository = textHighlightsRepository

    private suspend fun appendToHistory() {
        Logger.d("QuizVM", "appendToHistory: dbName='${state.value.databaseName}', sessionId='$sessionId'")
        try {
            val newSessionId = sessionRepository.appendToHistoryAsync(
                databaseName = state.value.databaseName,
                selectedSubjectIds = filterStateHolder.selectedSubjectIds.value,
                selectedSystemIds = filterStateHolder.selectedSystemIds.value,
                performanceFilter = filterStateHolder.performanceFilter.value,
                currentQuestionIndex = state.value.currentQuestionIndex,
                isLoggingEnabled = state.value.isLoggingEnabled,
                submissionMode = state.value.submissionMode,
                currentSessionId = sessionId,
            )
            Logger.d("QuizVM", "appendToHistory: result=$newSessionId")
            if (newSessionId.isNotBlank()) {
                sessionId = newSessionId
            }
        } catch (e: Exception) {
            Logger.e("QuizVM", "Error appending to history", e)
        }
    }

    fun setSessionId(id: String) {
        sessionId = id
    }

    fun getSessionId(): String = sessionId

    fun loadQuestion(
        index: Int,
        resetAnswerState: Boolean = true,
        appendToHistory: Boolean = true,
    ) {
        val ids = state.value.questionIds
        val questionId = ids.getOrNull(index)
        Logger.d("QuizVM", "loadQuestion: index=$index, ids.size=${ids.size}, questionId=$questionId, appendToHistory=$appendToHistory")
        if (questionId == null) {
            Logger.e("QuizVM", "loadQuestion EARLY RETURN: no question at index $index")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, currentPerformance = null) }
            val db = activeDatabaseHolder.databaseProvider.value
            try {
                val result = loadQuestionUseCase(
                    db = db,
                    questionId = questionId,
                    isLoggingEnabled = state.value.isLoggingEnabled,
                )
                _state.update { 
                    it.copy(currentQuestionIndex = index)
                      .copyWithQuestion(
                          question = result.question,
                          answers = result.answers,
                          resetAnswerState = resetAnswerState
                      )
                      .copy(currentPerformance = result.performance)
                }
                persistStateSnapshot()
                if (result.question != null) {
                    textHighlightsRepository.loadHighlightsForQuestion(
                        dbName = state.value.databaseName,
                        questionId = result.question.id
                    )
                }
            } catch (e: Exception) {
                Logger.e("QuizViewModel", "Error loading question $questionId", e)
                emitSnackbar("Failed to load question: ${e.message}")
            } finally {
                cacheManager.trimCachesIfNeeded(index)
                if (appendToHistory) {
                    appendToHistory()
                }
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadNext() {
        val currentState = state.value
        if (currentState.currentQuestion == null) {
            if (currentState.questionIds.isNotEmpty()) {
                loadQuestion(0)
            }
            return
        }
        val nextIndex = currentState.currentQuestionIndex + 1
        if (nextIndex < currentState.questionIds.size) {
            loadQuestion(nextIndex)
        }
    }

    fun loadPrevious() {
        val currentState = state.value
        if (currentState.currentQuestion == null) {
            if (currentState.questionIds.isNotEmpty()) {
                loadQuestion(0)
            }
            return
        }
        val previousIndex = currentState.currentQuestionIndex - 1
        if (previousIndex >= 0) {
            loadQuestion(previousIndex)
        }
    }

    fun onAnswerSelected(answerId: Long) {
        _state.update { it.copy(selectedAnswerId = answerId.toInt()) }
    }

    fun submitAnswer(timeTaken: Long) {
        val currentState = state.value
        val question = currentState.currentQuestion ?: return
        val selectedAnswerId = currentState.selectedAnswerId

        if (selectedAnswerId == null) {
            emitSnackbar("Please select an answer")
            return
        }

        if (currentState.answerSubmitted) return
        _state.update { it.copy(answerSubmitted = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val db = activeDatabaseHolder.databaseProvider.value
            try {
                if (state.value.isLoggingEnabled && db != null) {
                    val correctAnswer = currentState.currentAnswers.getOrNull(question.corrAns - 1)
                    val correctAnswerId = correctAnswer?.answerId?.toInt() ?: -1
                    db.logAnswer(
                        qid = question.id,
                        selectedAnswer = selectedAnswerId,
                        corrAnswer = correctAnswerId,
                        time = timeTaken,
                        sessionId = sessionId
                    )
                    updatePerformanceState(question.id, correctAnswerId, selectedAnswerId)
                }
            } catch (e: Exception) {
                _state.update { it.copy(answerSubmitted = false) }
                emitSnackbar("Error saving answer: ${e.message}")
            }
        }
    }

    private fun updatePerformanceState(
        questionId: Long,
        correctAnswerId: Int,
        selectedAnswerId: Int
    ) {
        val wasCorrect = correctAnswerId == selectedAnswerId
        val previous = state.value.currentPerformance

        val updated = if (previous != null) {
            previous.copy(
                lastCorrect = wasCorrect,
                everCorrect = previous.everCorrect || wasCorrect,
                everIncorrect = previous.everIncorrect || !wasCorrect,
                attempts = previous.attempts + 1,
                correctCount = previous.correctCount + (if (wasCorrect) 1 else 0),
                incorrectCount = previous.incorrectCount + (if (!wasCorrect) 1 else 0)
            )
        } else {
            QuestionPerformance(
                qid = questionId,
                lastCorrect = wasCorrect,
                everCorrect = wasCorrect,
                everIncorrect = !wasCorrect,
                attempts = 1,
                correctCount = if (wasCorrect) 1 else 0,
                incorrectCount = if (!wasCorrect) 1 else 0
            )
        }
        _state.update { it.copy(currentPerformance = updated) }
    }

    fun resetAnswerState() {
        _state.update { it.copy(selectedAnswerId = null, answerSubmitted = false) }
    }

    fun setLoadingState(isLoading: Boolean) {
        _state.update { it.copy(isLoading = isLoading) }
    }

    fun loadFilteredQuestionIds(
        updatePreviewCount: Boolean = true,
        startFromBeginning: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val db = activeDatabaseHolder.databaseProvider.value
            Logger.d("QuizVM", "loadFilteredQuestionIds: db=${db != null}, dbName='${state.value.databaseName}'")
            try {
                val currentState = state.value
                val ids = db?.getQuestionIds(
                    subjectIds = filterStateHolder.selectedSubjectIds.value.toList(),
                    systemIds = filterStateHolder.selectedSystemIds.value.toList(),
                    performanceFilter = filterStateHolder.performanceFilter.value
                ) ?: emptyList()
                Logger.d("QuizVM", "loadFilteredQuestionIds: got ${ids.size} IDs")

                if (ids.isEmpty()) {
                    _state.update {
                        it.copy(
                            questionIds = emptyList(),
                            currentQuestionIndex = 0,
                            currentQuestion = null,
                            currentAnswers = emptyList(),
                            selectedAnswerId = null,
                            answerSubmitted = false,
                            previewQuestionCount = if (updatePreviewCount) 0 else it.previewQuestionCount,
                            isLoading = false,
                        )
                    }
                    return@launch
                }

                val newIndex = if (startFromBeginning) {
                    0
                } else {
                    currentState.currentQuestionIndex.coerceIn(0, ids.lastIndex)
                }
                _state.update {
                    it.copy(
                        questionIds = ids,
                        currentQuestionIndex = newIndex,
                        previewQuestionCount = if (updatePreviewCount) ids.size else it.previewQuestionCount
                    )
                }
                loadQuestion(newIndex)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                Logger.e("QuizViewModel", "Error loading filtered questions", e)
            }
        }
    }

    fun clearCurrentQuestionLog() {
        val questionId = _state.value.currentQuestion?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val db = activeDatabaseHolder.databaseProvider.value
            try {
                db?.clearLogForQuestion(questionId)
                _state.update { it.copy(currentPerformance = null) }
                emitSnackbar("Log cleared for current question")
            } catch (e: Exception) {
                emitSnackbar("Failed to clear log: ${e.message}")
            }
        }
    }

    fun openMedia(urls: List<String>, startIndex: Int) {
        viewModelScope.launch {
            appIntentSink.send(com.medqb.app.shared.domain.AppIntent.OpenMedia(urls, startIndex))
        }
    }

    fun openHtmlFile(fileName: String) {
        viewModelScope.launch {
            appIntentSink.send(com.medqb.app.shared.domain.AppIntent.OpenHtmlFile(fileName))
        }
    }

    private fun observeSettings(repo: SettingsRepository) {
        viewModelScope.launch {
            repo.showMetadata.collect { visible ->
                _state.update { it.copy(showMetadata = visible) }
            }
        }
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            snackbarSink.emitSnackbar(message)
        }
    }

    fun saveScrollPosition(questionId: Long, scrollPosition: Int) {
        scrollPositionCache[questionId] = scrollPosition
    }

    fun getScrollPosition(questionId: Long): Int {
        return scrollPositionCache[questionId] ?: 0
    }

    fun clearScrollPosition(questionId: Long) {
        scrollPositionCache.remove(questionId)
    }
}
