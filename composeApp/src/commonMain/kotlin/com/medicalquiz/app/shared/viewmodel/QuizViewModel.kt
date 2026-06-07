package com.medicalquiz.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicalquiz.app.shared.data.ActiveDatabaseHolder
import com.medicalquiz.app.shared.data.CacheManager
import com.medicalquiz.app.shared.data.SettingsRepository
import com.medicalquiz.app.shared.data.TextHighlightsRepository
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.database.QuestionPerformance
import com.medicalquiz.app.shared.domain.QuizSessionBoundaryUseCase
import com.medicalquiz.app.shared.platform.Logger
import com.medicalquiz.app.shared.ui.state.QuizUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_SCROLL_CACHE_SIZE = 100

/**
 * Scoped ViewModel for the active Quiz session.
 * Manages question selection, answer submission, log updates, scroll caching, and text highlights.
 */
class QuizViewModel(
    internal val settingsRepository: SettingsRepository,
    private val textHighlightsRepository: TextHighlightsRepository,
    private val cacheManager: CacheManager,
    private val savedStateHandle: SavedStateHandle,
    dependencies: QuizViewModelDependencies,
    private val activeDatabaseHolder: ActiveDatabaseHolder,
) : ViewModel() {

    private val quizSessionBoundaryUseCase = dependencies.quizSessionBoundaryUseCase
    private val applyFiltersUseCase = dependencies.applyFiltersUseCase
    private val loadQuestionUseCase = dependencies.loadQuestionUseCase
    private val appIntentSink = dependencies.appIntentSink
    private val snackbarSink = dependencies.snackbarSink

    private companion object {
        const val KEY_DATABASE_NAME = "database_name"
        const val KEY_SELECTED_SUBJECT_IDS = "selected_subject_ids"
        const val KEY_SELECTED_SYSTEM_IDS = "selected_system_ids"
        const val KEY_PERFORMANCE_FILTER = "performance_filter"
        const val KEY_CURRENT_QUESTION_INDEX = "current_question_index"
        const val KEY_IS_LOGGING_ENABLED = "is_logging_enabled"
    }

    enum class SessionRestoreResult {
        Restored,
        NoSession,
        DatabaseMismatch,
    }

    private var settingsObservationJob: Job? = null
    private var sessionId = kotlin.random.Random.nextLong().toString()

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
        settingsObservationJob = observeSettings(settingsRepository)

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
        val savedSubjectIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_SUBJECT_IDS).orEmpty()
        val savedSystemIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_SYSTEM_IDS).orEmpty()
        val savedPerformanceName = savedStateHandle.get<String>(KEY_PERFORMANCE_FILTER)
        val savedQuestionIndex = savedStateHandle.get<Int>(KEY_CURRENT_QUESTION_INDEX) ?: 0
        val savedIsLoggingEnabled = savedStateHandle.get<Boolean>(KEY_IS_LOGGING_ENABLED) ?: true

        val savedFilter = savedPerformanceName
            ?.let { runCatching { PerformanceFilter.valueOf(it) }.getOrNull() }
            ?: PerformanceFilter.ALL

        _state.update {
            it.copy(
                databaseName = savedDatabaseName,
                selectedSubjectIds = savedSubjectIds.toSet(),
                selectedSystemIds = savedSystemIds.toSet(),
                performanceFilter = savedFilter,
                currentQuestionIndex = savedQuestionIndex.coerceAtLeast(0),
                isLoggingEnabled = savedIsLoggingEnabled,
            )
        }
    }

    private fun persistStateSnapshot(snapshot: QuizUiState = state.value) {
        savedStateHandle[KEY_DATABASE_NAME] = snapshot.databaseName
        savedStateHandle[KEY_SELECTED_SUBJECT_IDS] = snapshot.selectedSubjectIds.toList()
        savedStateHandle[KEY_SELECTED_SYSTEM_IDS] = snapshot.selectedSystemIds.toList()
        savedStateHandle[KEY_PERFORMANCE_FILTER] = snapshot.performanceFilter.name
        savedStateHandle[KEY_CURRENT_QUESTION_INDEX] = snapshot.currentQuestionIndex
        savedStateHandle[KEY_IS_LOGGING_ENABLED] = snapshot.isLoggingEnabled
    }

    fun getTextHighlightsRepository(): TextHighlightsRepository = textHighlightsRepository

    suspend fun restoreSession(): SessionRestoreResult {
        return when (val result = quizSessionBoundaryUseCase.restoreSessionForDatabase(state.value.databaseName)) {
            QuizSessionBoundaryUseCase.RestoreResult.NoSession -> SessionRestoreResult.NoSession
            QuizSessionBoundaryUseCase.RestoreResult.DatabaseMismatch -> SessionRestoreResult.DatabaseMismatch
            is QuizSessionBoundaryUseCase.RestoreResult.Restored -> {
                if (result.sessionId.isNotBlank()) {
                    setSessionId(result.sessionId)
                }
                _state.update {
                    it.copy(
                        selectedSubjectIds = result.selectedSubjectIds,
                        selectedSystemIds = result.selectedSystemIds,
                        performanceFilter = result.performanceFilter,
                        currentQuestionIndex = result.currentQuestionIndex,
                        isLoggingEnabled = result.isLoggingEnabled,
                    )
                }
                persistStateSnapshot()
                SessionRestoreResult.Restored
            }
        }
    }

    private suspend fun saveSession(appendToHistory: Boolean = true) {
        val sessionId = quizSessionBoundaryUseCase.saveSession(
            state = state.value,
            appendToHistory = appendToHistory,
        )
        if (sessionId.isNotBlank()) {
            setSessionId(sessionId)
        }
    }

    fun clearSession() {
        viewModelScope.launch(Dispatchers.IO) {
            quizSessionBoundaryUseCase.clearSession()
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
        val questionId = ids.getOrNull(index) ?: return

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
                _state.update { it.copy(isLoading = false) }
                cacheManager.trimCachesIfNeeded(index)
                saveSession(appendToHistory = appendToHistory)
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
        appendToHistory: Boolean = true,
        startFromBeginning: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val db = activeDatabaseHolder.databaseProvider.value
            try {
                val currentState = state.value
                val ids = db?.getQuestionIds(
                    subjectIds = currentState.selectedSubjectIds.toList(),
                    systemIds = currentState.selectedSystemIds.toList(),
                    performanceFilter = currentState.performanceFilter
                ) ?: emptyList()

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
                    saveSession(appendToHistory = false)
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
                loadQuestion(newIndex, appendToHistory = appendToHistory)
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
            appIntentSink.send(com.medicalquiz.app.shared.domain.AppIntent.OpenMedia(urls, startIndex))
        }
    }

    fun openHtmlFile(fileName: String) {
        viewModelScope.launch {
            appIntentSink.send(com.medicalquiz.app.shared.domain.AppIntent.OpenHtmlFile(fileName))
        }
    }

    private fun observeSettings(repo: SettingsRepository): Job {
        return viewModelScope.launch {
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

    override fun onCleared() {
        settingsObservationJob?.cancel()
        super.onCleared()
    }
}
