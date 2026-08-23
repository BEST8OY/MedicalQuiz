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
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.LoadQuestionUseCase
import com.medqb.app.shared.domain.SnackbarSink
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.ui.state.QuizUiState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private companion object {
        const val KEY_DATABASE_NAME = "databaseName"
        const val KEY_ENTRY_NAME = "entryName"
        const val KEY_CURRENT_QUESTION_INDEX = "currentQuestionIndex"
        const val KEY_IS_LOGGING_ENABLED = "isLoggingEnabled"
        const val KEY_SUBMISSION_MODE = "submissionMode"
        const val KEY_SESSION_ID = "sessionId"
    }

    private val _state = MutableStateFlow(QuizUiState.EMPTY)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    val toolbarTitle = state
        .map { it.entryName.ifBlank { it.databaseName } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val sessionIdState: MutableStateFlow<String> = savedStateHandle.getMutableStateFlow(KEY_SESSION_ID, "")
    private val sessionId: String
        get() = sessionIdState.value
    private var filteredIdsJob: Job? = null
    // Name of the database whose question ids were last (re)loaded. Distinguishes
    // "first load / db switched" from "filters legitimately produced zero results".
    private var loadedForDbName: String? = null
    private var loadSeq = 0L

    /**
     * Navigation requests. A StateFlow (not a SharedFlow) so an emission made before
     * the collector subscribes is never lost; [LoadRequest.seq] makes consecutive
     * requests distinguishable, and collectLatest cancels any in-flight load when a
     * newer request arrives — latest navigation wins.
     */
    private val loadRequests = MutableStateFlow<LoadRequest?>(null)

    private data class LoadRequest(
        val seq: Long,
        val index: Int,
        val resetAnswerState: Boolean,
        val appendToHistory: Boolean,
    )

    private fun updateSessionId(id: String) {
        sessionIdState.value = id
    }

    init {
        restoreFromSavedState()
        observeSettings()

        viewModelScope.launch {
            activeDatabaseHolder.activeDatabase.collect { active ->
                val dbName = active?.name ?: return@collect

                val isFirstLoad = loadedForDbName == null
                val dbChanged = dbName != loadedForDbName
                if (isFirstLoad || dbChanged) {
                    loadedForDbName = dbName
                    _state.update { it.copy(databaseName = dbName, questionIds = emptyList()) }
                    // First load after process restore resumes the saved position;
                    // switching to a different QBank restarts at the first question.
                    loadFilteredQuestionIds(
                        updatePreviewCount = true,
                        startFromBeginning = !isFirstLoad
                    )
                }
            }
        }

        viewModelScope.launch {
            loadRequests.collectLatest { request ->
                if (request == null) return@collectLatest

                val ids = state.value.questionIds
                val questionId = ids.getOrNull(request.index) ?: return@collectLatest

                _state.update { it.copy(isLoading = true, currentPerformance = null) }
                val active = activeDatabaseHolder.activeDatabase.value
                val db = active?.provider
                try {
                    val result = loadQuestionUseCase(
                        db = db,
                        dbName = state.value.databaseName,
                        questionId = questionId,
                        isLoggingEnabled = state.value.isLoggingEnabled,
                    )
                    currentCoroutineContext().ensureActive()
                    // The database was switched while this load was in flight —
                    // drop the result instead of publishing stale question state.
                    if (active != null && activeDatabaseHolder.activeDatabase.value !== active) {
                        return@collectLatest
                    }
                    _state.update {
                        it.copy(currentQuestionIndex = request.index)
                            .copyWithQuestion(
                                question = result.question,
                                answers = result.answers,
                                correctAnswerId = result.correctAnswerId,
                                questionHighlights = result.questionHighlights,
                                explanationHighlights = result.explanationHighlights,
                                resetAnswerState = request.resetAnswerState
                            )
                            .copy(currentPerformance = result.performance)
                    }
                    persistStateSnapshot()
                    if (request.appendToHistory) {
                        appendToHistory()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e("QuizViewModel", "Error loading question $questionId", e)
                    emitSnackbar("Failed to load question: ${e.message}")
                } finally {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun restoreFromSavedState() {
        val savedDatabaseName = savedStateHandle.get<String>(KEY_DATABASE_NAME).orEmpty()
        val savedEntryName = savedStateHandle.get<String>(KEY_ENTRY_NAME).orEmpty()
        val savedQuestionIndex = savedStateHandle.get<Int>(KEY_CURRENT_QUESTION_INDEX) ?: 0
        val savedIsLoggingEnabled = savedStateHandle.get<Boolean>(KEY_IS_LOGGING_ENABLED)
            ?: settingsRepository.isLoggingEnabled.value
        val savedSubmissionModeStr = savedStateHandle.get<String>(KEY_SUBMISSION_MODE)
        val savedSubmissionMode = savedSubmissionModeStr
            ?.let { runCatching { SubmissionMode.valueOf(it) }.getOrNull() }
            ?: settingsRepository.submissionMode.value

        _state.update {
            it.copy(
                databaseName = savedDatabaseName,
                entryName = savedEntryName,
                currentQuestionIndex = savedQuestionIndex.coerceAtLeast(0),
                isLoggingEnabled = savedIsLoggingEnabled,
                submissionMode = savedSubmissionMode,
            )
        }
    }

    private fun persistStateSnapshot(snapshot: QuizUiState = state.value) {
        savedStateHandle[KEY_DATABASE_NAME] = snapshot.databaseName
        savedStateHandle[KEY_ENTRY_NAME] = snapshot.entryName
        savedStateHandle[KEY_CURRENT_QUESTION_INDEX] = snapshot.currentQuestionIndex
        savedStateHandle[KEY_IS_LOGGING_ENABLED] = snapshot.isLoggingEnabled
        savedStateHandle[KEY_SUBMISSION_MODE] = snapshot.submissionMode.name
    }

    /**
     * Highlight intents. Each captures the question the user was viewing at dispatch
     * time; the resulting DB write targets that question, and the refreshed list is
     * only published while the UI still displays it — a mutation can never leak into
     * another question's state.
     */
    fun addHighlight(
        section: HighlightSection,
        startOffset: Int,
        endOffset: Int,
        highlightedText: String,
        color: HighlightColor = HighlightColor.YELLOW,
    ) {
        mutateHighlights { dbName, questionId ->
            textHighlightsRepository.addHighlight(
                dbName = dbName,
                questionId = questionId,
                section = section,
                startOffset = startOffset,
                endOffset = endOffset,
                highlightedText = highlightedText,
                color = color,
            )
        }
    }

    fun removeHighlight(highlightId: Long) {
        mutateHighlights { dbName, questionId ->
            textHighlightsRepository.removeHighlight(dbName, questionId, highlightId)
        }
    }

    fun changeHighlightColor(highlightId: Long, color: HighlightColor) {
        mutateHighlights { dbName, questionId ->
            textHighlightsRepository.updateHighlightColor(dbName, questionId, highlightId, color)
        }
    }

    private fun mutateHighlights(
        block: suspend (dbName: String, questionId: Long) -> List<TextHighlight>
    ) {
        val snapshot = state.value
        val dbName = snapshot.databaseName
        val questionId = snapshot.currentQuestion?.id ?: return

        viewModelScope.launch(ioDispatcher) {
            try {
                val updated = block(dbName, questionId)
                publishHighlights(dbName, questionId, updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("QuizViewModel", "Error updating highlights for question $questionId", e)
            }
        }
    }

    private fun publishHighlights(dbName: String, questionId: Long, updated: List<TextHighlight>) {
        _state.update { current ->
            // The user navigated away mid-write — this result belongs to another
            // question than the one now displayed.
            if (current.databaseName != dbName || current.currentQuestion?.id != questionId) {
                current
            } else {
                current.copy(
                    questionHighlights = updated.filter { it.section == HighlightSection.QUESTION },
                    explanationHighlights = updated.filter { it.section == HighlightSection.EXPLANATION },
                )
            }
        }
    }

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
                entryName = state.value.entryName,
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
        loadRequests.value = LoadRequest(++loadSeq, index, resetAnswerState, appendToHistory)
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

        val wasAlreadySubmitted = _state.getAndUpdate { it.copy(answerSubmitted = true) }.answerSubmitted
        if (wasAlreadySubmitted) return

        viewModelScope.launch(ioDispatcher) {
            val active = activeDatabaseHolder.activeDatabase.value
            val db = active?.provider
            try {
                if (state.value.isLoggingEnabled && db != null) {
                    val correctAnswerId = currentState.correctAnswerId
                    // A question whose answer key can't be derived (malformed bank
                    // row) must not reach the log: a corrAnswer sentinel would grade
                    // the attempt permanently incorrect in every stat query.
                    if (correctAnswerId == null) {
                        emitSnackbar("Question has no valid answer key — result not saved")
                        return@launch
                    }
                    db.logAnswer(
                        qid = question.id,
                        selectedAnswer = selectedAnswerId,
                        corrAnswer = correctAnswerId,
                        time = timeTaken,
                        sessionId = sessionId
                    )
                    // Only merge performance into the UI while the user is still on the
                    // same question of the same database — otherwise the stats would be
                    // attributed to whatever is now displayed.
                    val stillOnSameQuestion =
                        activeDatabaseHolder.activeDatabase.value === active &&
                            state.value.currentQuestion?.id == question.id
                    if (stillOnSameQuestion) {
                        updatePerformanceState(question.id, correctAnswerId, selectedAnswerId)
                    }
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

    private fun loadFilteredQuestionIds(
        updatePreviewCount: Boolean = true,
        startFromBeginning: Boolean = false,
    ) {
        filteredIdsJob?.cancel()
        filteredIdsJob = viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(isLoading = true) }
            val active = activeDatabaseHolder.activeDatabase.value
            val db = active?.provider
            try {
                val currentState = state.value
                val ids = db?.getQuestionIds(
                    subjectIds = filterStateHolder.selectedSubjectIds.value.toList(),
                    systemIds = filterStateHolder.selectedSystemIds.value.toList(),
                    performanceFilter = filterStateHolder.performanceFilter.value
                ) ?: emptyList()

                // Database switched mid-query — a newer load for the new database is
                // responsible for publishing state.
                if (active != null && activeDatabaseHolder.activeDatabase.value !== active) {
                    return@launch
                }

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
        viewModelScope.launch(ioDispatcher) {
            val active = activeDatabaseHolder.activeDatabase.value
            val db = active?.provider
            try {
                db?.clearLogForQuestion(questionId)
                if (active != null && activeDatabaseHolder.activeDatabase.value !== active) {
                    return@launch
                }
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
            .distinctUntilChanged()
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

    /**
     * Public entrypoint for composables nested under this ViewModel to surface a
     * typed snackbar (e.g. an undo/copy Action) via the shared [SnackbarDispatcher].
     *
     * This exists so leaf UI such as [com.medqb.app.shared.ui.richtext.SelectableHighlightText]
     * can route snackbar requests through the ViewModel's sink without holding the dispatcher.
     */
    fun emitSnackbar(message: SnackbarMessage) {
        viewModelScope.launch {
            snackbarSink.emitSnackbar(message)
        }
    }
}
