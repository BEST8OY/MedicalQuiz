package com.medqb.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.domain.LoadQuestionUseCase
import com.medqb.app.shared.domain.SnackbarSink
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.ui.state.QuizUiState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class QuizViewModel(
    private val settingsRepository: SettingsRepository,
    private val textHighlightsRepository: TextHighlightsRepository,
    private val sessionRepository: QuizSessionRepository,
    private val savedStateHandle: SavedStateHandle,
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val loadQuestionUseCase: LoadQuestionUseCase,
    private val snackbarSink: SnackbarSink,
    private val filterStateHolder: FilterStateHolder,
) : ViewModel() {

    private companion object {
        const val KEY_DATABASE_NAME = "database_name"
        const val KEY_CURRENT_QUESTION_INDEX = "current_question_index"
        const val KEY_IS_LOGGING_ENABLED = "is_logging_enabled"
        const val KEY_SUBMISSION_MODE = "submission_mode"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SELECTED_SUBJECT_IDS = "selected_subject_ids"
        const val KEY_SELECTED_SYSTEM_IDS = "selected_system_ids"
        const val KEY_PERFORMANCE_FILTER = "performance_filter"
    }

    private val _state = MutableStateFlow(QuizUiState.EMPTY)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    val toolbarTitle = state
        .map { it.databaseName }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private var sessionId: String = ""
    private val loadRequests = MutableSharedFlow<LoadRequest>(extraBufferCapacity = 1)

    private data class LoadRequest(
        val index: Int,
        val resetAnswerState: Boolean,
        val appendToHistory: Boolean,
    )

    private fun updateSessionId(id: String) {
        sessionId = id
        savedStateHandle[KEY_SESSION_ID] = id
    }

    init {
        restoreFromSavedState()
        observeSettings()

        sessionId = savedStateHandle.get<String>(KEY_SESSION_ID).orEmpty()

        val restoredId = filterStateHolder.consumePendingHistoryEntryId()
        if (!restoredId.isNullOrBlank()) {
            updateSessionId(restoredId)
        }

        val restoredIndex = filterStateHolder.consumePendingHistoryQuestionIndex()
        if (restoredIndex > 0) {
            _state.update { it.copy(currentQuestionIndex = restoredIndex) }
        }

        viewModelScope.launch {
            activeDatabaseHolder.databaseName.collect { dbName ->
                if (dbName.isNotEmpty() && (dbName != _state.value.databaseName || _state.value.questionIds.isEmpty())) {
                    val startFromBeginning = _state.value.currentQuestionIndex <= 0
                    _state.update { it.copy(databaseName = dbName, questionIds = emptyList()) }
                    loadFilteredQuestionIds(
                        updatePreviewCount = true,
                        startFromBeginning = startFromBeginning
                    )
                }
            }
        }

        loadRequests
            .flatMapLatest { request ->
                flow {
                    val ids = state.value.questionIds
                    val questionId = ids.getOrNull(request.index) ?: return@flow

                    _state.update { it.copy(isLoading = true, currentPerformance = null) }
                    val db = activeDatabaseHolder.databaseProvider.value
                    try {
                        val result = loadQuestionUseCase(
                            db = db,
                            questionId = questionId,
                            isLoggingEnabled = state.value.isLoggingEnabled,
                        )
                        currentCoroutineContext().ensureActive()
                        _state.update {
                            it.copy(currentQuestionIndex = request.index)
                                .copyWithQuestion(
                                    question = result.question,
                                    answers = result.answers,
                                    resetAnswerState = request.resetAnswerState
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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.e("QuizViewModel", "Error loading question $questionId", e)
                        emitSnackbar("Failed to load question: ${e.message}")
                    } finally {
                        if (currentCoroutineContext()[Job]?.isActive == true) {
                            if (request.appendToHistory) {
                                appendToHistory()
                            }
                            _state.update { it.copy(isLoading = false) }
                        }
                    }
                    emit(Unit)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun restoreFromSavedState() {
        val savedDatabaseName = savedStateHandle.get<String>(KEY_DATABASE_NAME).orEmpty()
        val savedQuestionIndex = savedStateHandle.get<Int>(KEY_CURRENT_QUESTION_INDEX) ?: 0
        val savedIsLoggingEnabled = filterStateHolder.consumePendingIsLoggingEnabled()
            ?: savedStateHandle.get<Boolean>(KEY_IS_LOGGING_ENABLED)
            ?: settingsRepository.isLoggingEnabled.value
        val savedSubmissionMode = filterStateHolder.consumePendingSubmissionMode()
            ?: savedStateHandle.get<String>(KEY_SUBMISSION_MODE)
                ?.let { runCatching { SubmissionMode.valueOf(it) }.getOrNull() }
            ?: settingsRepository.submissionMode.value

        val savedSubjectIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_SUBJECT_IDS)?.toSet()
        if (savedSubjectIds != null) {
            filterStateHolder.updateSubjectIds(savedSubjectIds)
        }
        val savedSystemIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_SYSTEM_IDS)?.toSet()
        if (savedSystemIds != null) {
            filterStateHolder.updateSystemIds(savedSystemIds)
        }
        val savedPerformanceFilterName = savedStateHandle.get<String>(KEY_PERFORMANCE_FILTER)
        if (savedPerformanceFilterName != null) {
            runCatching { PerformanceFilter.valueOf(savedPerformanceFilterName) }.getOrNull()?.let {
                filterStateHolder.updatePerformanceFilter(it)
            }
        }

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
        viewModelScope.launch(Dispatchers.Main) {
            savedStateHandle[KEY_DATABASE_NAME] = snapshot.databaseName
            savedStateHandle[KEY_CURRENT_QUESTION_INDEX] = snapshot.currentQuestionIndex
            savedStateHandle[KEY_IS_LOGGING_ENABLED] = snapshot.isLoggingEnabled
            savedStateHandle[KEY_SUBMISSION_MODE] = snapshot.submissionMode.name
            savedStateHandle[KEY_SELECTED_SUBJECT_IDS] = filterStateHolder.selectedSubjectIds.value.toList()
            savedStateHandle[KEY_SELECTED_SYSTEM_IDS] = filterStateHolder.selectedSystemIds.value.toList()
            savedStateHandle[KEY_PERFORMANCE_FILTER] = filterStateHolder.performanceFilter.value.name
        }
    }

    val highlightsRepository: TextHighlightsRepository
        get() = textHighlightsRepository

    private suspend fun appendToHistory() {
        try {
            val newSessionId = sessionRepository.appendToHistory(
                databaseName = state.value.databaseName,
                selectedSubjectIds = filterStateHolder.selectedSubjectIds.value,
                selectedSystemIds = filterStateHolder.selectedSystemIds.value,
                performanceFilter = filterStateHolder.performanceFilter.value,
                currentQuestionIndex = state.value.currentQuestionIndex,
                isLoggingEnabled = state.value.isLoggingEnabled,
                submissionMode = state.value.submissionMode,
                currentSessionId = sessionId,
            )
            if (newSessionId.isNotBlank()) {
                updateSessionId(newSessionId)
            }
        } catch (e: Exception) {
            Logger.e("QuizViewModel", "Error appending to history", e)
        }
    }

    fun loadQuestion(
        index: Int,
        resetAnswerState: Boolean = true,
        appendToHistory: Boolean = true,
    ) {
        val ids = state.value.questionIds
        if (ids.getOrNull(index) == null) return
        loadRequests.tryEmit(LoadRequest(index, resetAnswerState, appendToHistory))
    }

    fun loadNext() {
        val s = state.value
        val index = if (s.currentQuestion == null) {
            if (s.questionIds.isNotEmpty()) 0 else return
        } else {
            val next = s.currentQuestionIndex + 1
            if (next >= s.questionIds.size) return
            next
        }
        loadQuestion(index)
    }

    fun loadPrevious() {
        val s = state.value
        val index = if (s.currentQuestion == null) {
            if (s.questionIds.isNotEmpty()) 0 else return
        } else {
            val prev = s.currentQuestionIndex - 1
            if (prev < 0) return
            prev
        }
        loadQuestion(index)
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
            } catch (e: CancellationException) {
                throw e
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
            try {
                val currentState = state.value
                val ids = db?.getQuestionIds(
                    subjectIds = filterStateHolder.selectedSubjectIds.value.toList(),
                    systemIds = filterStateHolder.selectedSystemIds.value.toList(),
                    performanceFilter = filterStateHolder.performanceFilter.value
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitSnackbar("Failed to clear log: ${e.message}")
            }
        }
    }

    private fun observeSettings() {
        combine(
            settingsRepository.showMetadata,
            settingsRepository.fontScalePreference,
        ) { metadata, fontScale -> metadata to fontScale }
            .onEach { (metadata, fontScale) ->
                _state.update { it.copy(showMetadata = metadata, fontScalePreference = fontScale) }
            }
            .launchIn(viewModelScope)
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            snackbarSink.emitSnackbar(SnackbarMessage.Simple(message))
        }
    }
}
