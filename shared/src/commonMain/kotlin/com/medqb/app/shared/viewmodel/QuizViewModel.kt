package com.medqb.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.ActiveDatabase
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.MarkedQuestionsRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.data.canonicalizeDbName
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.QuestionFilter
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.LoadQuestionUseCase
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.domain.SnackbarSink
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.ui.state.QuizUiState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class QuizViewModel(
    private val route: MedQBRoutes.Quiz = MedQBRoutes.Quiz(),
    private val settingsRepository: SettingsRepository,
    private val textHighlightsRepository: TextHighlightsRepository,
    private val markedQuestionsRepository: MarkedQuestionsRepository,
    private val sessionRepository: QuizSessionRepository,
    private val savedStateHandle: SavedStateHandle,
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val loadQuestionUseCase: LoadQuestionUseCase,
    private val snackbarSink: SnackbarSink,
    private val filterStateHolder: FilterStateHolder,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {

    private companion object {
        const val KEY_DATABASE_NAME = "databaseName"
        const val KEY_ENTRY_NAME = "entryName"
        const val KEY_CURRENT_QUESTION_INDEX = "currentQuestionIndex"
        const val KEY_CURRENT_QUESTION_ID = "currentQuestionId"
        const val KEY_IS_LOGGING_ENABLED = "isLoggingEnabled"
        const val KEY_SUBMISSION_MODE = "submissionMode"
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_LOCK_LOGGING_SETTING = "lockLoggingSetting"
        const val KEY_LOCK_SUBMISSION_MODE = "lockSubmissionMode"
        const val KEY_FILTER_INITIALIZED = "filterInitialized"
        const val KEY_FILTER_SELECTED_SUBJECTS = "filterSelectedSubjects"
        const val KEY_FILTER_EXCLUDED_SUBJECTS = "filterExcludedSubjects"
        const val KEY_FILTER_SELECTED_SYSTEMS = "filterSelectedSystems"
        const val KEY_FILTER_EXCLUDED_SYSTEMS = "filterExcludedSystems"
        const val KEY_FILTER_STATUS = "filterStatus"
        const val KEY_FILTER_DIFFICULTY_TIERS = "filterDifficultyTiers"
    }

    private var isLoggingSettingLocked: Boolean = resolveInitialSettingLock(
        lockKey = KEY_LOCK_LOGGING_SETTING,
        explicitRouteValue = route.isLoggingEnabled != null,
        savedValuePresent = savedStateHandle.contains(KEY_IS_LOGGING_ENABLED),
    )
    private var isSubmissionModeLocked: Boolean = resolveInitialSettingLock(
        lockKey = KEY_LOCK_SUBMISSION_MODE,
        explicitRouteValue = route.submissionMode != null,
        savedValuePresent = savedStateHandle.contains(KEY_SUBMISSION_MODE),
    )

    private val sessionIdState: MutableStateFlow<String> = savedStateHandle.getMutableStateFlow(KEY_SESSION_ID, route.sessionId)
    private val sessionId: String
        get() = sessionIdState.value

    private var currentQuestionId: Long? = savedStateHandle.get<Long>(KEY_CURRENT_QUESTION_ID)
        ?: route.initialQuestionId

    private var expectedDatabaseName: String? = (
        savedStateHandle.get<String>(KEY_DATABASE_NAME)?.takeIf { it.isNotBlank() }
            ?: route.databaseName
        ).let(::canonicalizeDbName).takeIf { it.isNotBlank() }

    private var sessionFilter: QuestionFilter = restoreInitialFilter()

    private val _state = MutableStateFlow(restoreInitialState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    val toolbarTitle: StateFlow<String> = state
        .map { it.toolbarTitle }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value.toolbarTitle)

    private var filteredIdsJob: Job? = null
    private var loadQuestionJob: Job? = null
    // Reference to the currently active ActiveDatabase (cleared on null emissions).
    private var loadedForActiveDb: ActiveDatabase? = null
    // Canonical name of the last database loaded by this ViewModel instance.
    // Preserved across `active == null` gaps so bankA -> null -> bankB resets the session.
    private var lastLoadedDbName: String? = null
    private var questionDisplayedMark: TimeMark? = null
    private val markWriteMutex = Mutex()
    private var markMutationSeq = 0L
    private val latestMarkSeqByQuestion = mutableMapOf<Pair<String, Long>, Long>()

    private fun resolveInitialSettingLock(
        lockKey: String,
        explicitRouteValue: Boolean,
        savedValuePresent: Boolean,
    ): Boolean {
        savedStateHandle.get<Boolean>(lockKey)?.let { return it }
        val isRestoredSession = (savedStateHandle.get<String>(KEY_SESSION_ID) ?: route.sessionId).isNotBlank()
        val locked = isRestoredSession || explicitRouteValue || savedValuePresent
        savedStateHandle[lockKey] = locked
        return locked
    }

    private fun updateSessionId(id: String) {
        sessionIdState.value = id
    }

    init {
        observeSettings()

        viewModelScope.launch {
            activeDatabaseHolder.openFailure.collect { failure ->
                if (failure == null || loadedForActiveDb != null) return@collect
                val expected = expectedDatabaseName
                if (expected == null || failure.dbName == expected) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = failure.message,
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            activeDatabaseHolder.activeDatabase.collect { active ->
                if (active == null) {
                    if (loadedForActiveDb != null) {
                        loadQuestionJob?.cancel()
                        loadQuestionJob = null
                        filteredIdsJob?.cancel()
                        filteredIdsJob = null
                        loadedForActiveDb = null
                        questionDisplayedMark = null
                        _state.update {
                            it.copy(
                                databaseName = "",
                                questionIds = emptyList(),
                                currentQuestionIndex = -1,
                                currentQuestion = null,
                                currentAnswers = emptyList(),
                                selectedAnswerId = null,
                                correctAnswerId = null,
                                questionHighlights = emptyList(),
                                explanationHighlights = emptyList(),
                                currentPerformance = null,
                                answerSubmitted = false,
                                isLoading = false,
                                isCurrentQuestionMarked = false,
                                markedQuestionIds = emptySet(),
                            )
                        }
                    }
                    return@collect
                }

                val canonicalDbName = canonicalizeDbName(active.name)
                val expected = expectedDatabaseName
                if (lastLoadedDbName == null && !expected.isNullOrBlank() && canonicalDbName != expected) {
                    // Waiting for the target database of a restored/process-recreated session to finish opening.
                    return@collect
                }
                if (active === loadedForActiveDb) {
                    return@collect
                }

                val isDifferentDatabase = lastLoadedDbName != null && lastLoadedDbName != canonicalDbName

                loadQuestionJob?.cancel()
                loadQuestionJob = null
                loadedForActiveDb = active
                lastLoadedDbName = canonicalDbName
                expectedDatabaseName = null
                savedStateHandle[KEY_DATABASE_NAME] = canonicalDbName

                if (isDifferentDatabase) {
                    updateSessionId("")
                    currentQuestionId = null
                    savedStateHandle.remove<Long>(KEY_CURRENT_QUESTION_ID)
                    sessionFilter = filterStateHolder.filter.value
                    saveFilterToSavedState(sessionFilter)
                    isLoggingSettingLocked = false
                    isSubmissionModeLocked = false
                    savedStateHandle[KEY_LOCK_LOGGING_SETTING] = false
                    savedStateHandle[KEY_LOCK_SUBMISSION_MODE] = false
                    savedStateHandle[KEY_ENTRY_NAME] = ""
                    savedStateHandle[KEY_IS_LOGGING_ENABLED] = settingsRepository.isLoggingEnabled.value
                    savedStateHandle[KEY_SUBMISSION_MODE] = settingsRepository.submissionMode.value.name
                }

                _state.update {
                    it.copy(
                        databaseName = canonicalDbName,
                        entryName = if (isDifferentDatabase) "" else it.entryName,
                        isLoggingEnabled = if (isDifferentDatabase) settingsRepository.isLoggingEnabled.value else it.isLoggingEnabled,
                        submissionMode = if (isDifferentDatabase) settingsRepository.submissionMode.value else it.submissionMode,
                        questionIds = emptyList(),
                        isCurrentQuestionMarked = false,
                        markedQuestionIds = emptySet(),
                    )
                }

                // First load / same-DB reopen resumes the saved position;
                // switching to a different QBank starts a fresh session at index 0.
                loadFilteredQuestionIds(
                    active = active,
                    startFromBeginning = isDifferentDatabase,
                )
            }
        }
    }

    private fun enqueueHistoryWrite(snapshot: QuizUiState, questionId: Long?) {
        val activeSessionId = sessionId
        if (activeSessionId.isBlank() || snapshot.databaseName.isBlank()) return
        sessionRepository.enqueueHistoryUpdate(
            databaseName = snapshot.databaseName,
            selectedSubjectIds = sessionFilter.selectedSubjectIds,
            excludedSubjectIds = sessionFilter.excludedSubjectIds,
            selectedSystemIds = sessionFilter.selectedSystemIds,
            excludedSystemIds = sessionFilter.excludedSystemIds,
            statusFilter = sessionFilter.statusFilter,
            currentQuestionIndex = snapshot.currentQuestionIndex,
            currentQuestionId = questionId,
            isLoggingEnabled = snapshot.isLoggingEnabled,
            submissionMode = snapshot.submissionMode,
            currentSessionId = activeSessionId,
            entryName = snapshot.entryName,
            selectedDifficultyTiers = sessionFilter.selectedDifficultyTiers,
        )
    }

    private fun restoreInitialFilter(): QuestionFilter {
        if (savedStateHandle.get<Boolean>(KEY_FILTER_INITIALIZED) == true) {
            return readFilterFromSavedState()
        }
        if (sessionId.isBlank()) {
            val snapshot = filterStateHolder.filter.value
            saveFilterToSavedState(snapshot)
            return snapshot
        }
        return filterStateHolder.filter.value
    }

    private fun readFilterFromSavedState(): QuestionFilter {
        val selectedSubjects = savedStateHandle.get<LongArray>(KEY_FILTER_SELECTED_SUBJECTS)?.toSet().orEmpty()
        val excludedSubjects = savedStateHandle.get<LongArray>(KEY_FILTER_EXCLUDED_SUBJECTS)?.toSet().orEmpty()
        val selectedSystems = savedStateHandle.get<LongArray>(KEY_FILTER_SELECTED_SYSTEMS)?.toSet().orEmpty()
        val excludedSystems = savedStateHandle.get<LongArray>(KEY_FILTER_EXCLUDED_SYSTEMS)?.toSet().orEmpty()
        val statusFilter = savedStateHandle.get<String>(KEY_FILTER_STATUS)
            ?.let(StatusFilter.Companion::deserialize)
            ?: StatusFilter.ALL
        val difficultyTiers = savedStateHandle.get<String>(KEY_FILTER_DIFFICULTY_TIERS)
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { token ->
                token.trim().takeIf { it.isNotEmpty() }?.let { t ->
                    runCatching { DifficultyTier.valueOf(t) }.getOrNull()
                }
            }
            ?.toSet()
            .orEmpty()
        return QuestionFilter.create(
            selectedSubjectIds = selectedSubjects,
            excludedSubjectIds = excludedSubjects,
            selectedSystemIds = selectedSystems,
            excludedSystemIds = excludedSystems,
            statusFilter = statusFilter,
            selectedDifficultyTiers = difficultyTiers,
        )
    }

    private fun saveFilterToSavedState(filter: QuestionFilter) {
        savedStateHandle[KEY_FILTER_SELECTED_SUBJECTS] = filter.selectedSubjectIds.toLongArray()
        savedStateHandle[KEY_FILTER_EXCLUDED_SUBJECTS] = filter.excludedSubjectIds.toLongArray()
        savedStateHandle[KEY_FILTER_SELECTED_SYSTEMS] = filter.selectedSystemIds.toLongArray()
        savedStateHandle[KEY_FILTER_EXCLUDED_SYSTEMS] = filter.excludedSystemIds.toLongArray()
        savedStateHandle[KEY_FILTER_STATUS] = filter.statusFilter.serialize()
        savedStateHandle[KEY_FILTER_DIFFICULTY_TIERS] = filter.selectedDifficultyTiers.joinToString(",") { it.name }
        savedStateHandle[KEY_FILTER_INITIALIZED] = true
    }

    private fun restoreInitialState(): QuizUiState {
        val savedDatabaseName = savedStateHandle.get<String>(KEY_DATABASE_NAME)
            ?.let(::canonicalizeDbName)
            .orEmpty()
        val savedEntryName = savedStateHandle.get<String>(KEY_ENTRY_NAME) ?: route.entryName
        val savedQuestionIndex = savedStateHandle.get<Int>(KEY_CURRENT_QUESTION_INDEX) ?: route.initialQuestionIndex
        val savedIsLoggingEnabled = savedStateHandle.get<Boolean>(KEY_IS_LOGGING_ENABLED)
            ?: route.isLoggingEnabled
            ?: settingsRepository.isLoggingEnabled.value
        val savedSubmissionModeStr = savedStateHandle.get<String>(KEY_SUBMISSION_MODE)
        val savedSubmissionMode = savedSubmissionModeStr
            ?.let { runCatching { SubmissionMode.valueOf(it) }.getOrNull() }
            ?: route.submissionMode
            ?: settingsRepository.submissionMode.value

        return QuizUiState.EMPTY.copy(
            databaseName = savedDatabaseName,
            entryName = savedEntryName,
            currentQuestionIndex = savedQuestionIndex.coerceAtLeast(0),
            isLoggingEnabled = savedIsLoggingEnabled,
            submissionMode = savedSubmissionMode,
            isLoading = expectedDatabaseName != null,
        )
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
        blockKey: String,
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
                blockKey = blockKey,
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

        viewModelScope.launch {
            try {
                val updated = withContext(ioDispatcher) {
                    block(dbName, questionId)
                }
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

    fun loadQuestion(
        index: Int,
        resetAnswerState: Boolean = true,
        appendToHistory: Boolean = true,
    ) {
        val ids = state.value.questionIds
        val questionId = ids.getOrNull(index) ?: return

        loadQuestionJob?.cancel()
        loadQuestionJob = viewModelScope.launch {
            val active = activeDatabaseHolder.activeDatabase.value
            val db = active?.provider
            val activeDbName = active?.name?.let(::canonicalizeDbName).orEmpty()
            val isLogging = state.value.isLoggingEnabled
            _state.update { it.copy(isLoading = true, errorMessage = null, currentPerformance = null) }
            try {
                val result = withContext(ioDispatcher) {
                    loadQuestionUseCase(
                        db = db,
                        dbName = activeDbName,
                        questionId = questionId,
                        isLoggingEnabled = isLogging,
                    )
                }
                ensureActive()
                // The database was switched while this load was in flight —
                // drop the result instead of publishing stale question state.
                if (active != null && activeDatabaseHolder.activeDatabase.value !== active) {
                    return@launch
                }
                if (result.question != null) {
                    questionDisplayedMark = timeSource.markNow()
                    if (appendToHistory && sessionId.isBlank() && activeDbName.isNotBlank()) {
                        updateSessionId(sessionRepository.generateSessionId(activeDbName))
                    }
                    currentQuestionId = result.question.id
                    savedStateHandle[KEY_CURRENT_QUESTION_ID] = result.question.id
                }
                val updatedState = _state.updateAndGet {
                    val updatedMarkedIds = if (result.question != null) {
                        if (result.isMarked) it.markedQuestionIds + result.question.id
                        else it.markedQuestionIds - result.question.id
                    } else it.markedQuestionIds
                    it.copy(
                        currentQuestionIndex = index,
                        markedQuestionIds = updatedMarkedIds,
                        isLoading = false,
                    )
                        .copyWithQuestion(
                            question = result.question,
                            answers = result.answers,
                            correctAnswerId = result.correctAnswerId,
                            questionHighlights = result.questionHighlights,
                            explanationHighlights = result.explanationHighlights,
                            isMarked = result.isMarked,
                            resetAnswerState = resetAnswerState
                        )
                        .copy(currentPerformance = result.performance)
                }
                if (result.question != null) {
                    persistStateSnapshot(updatedState)
                    if (appendToHistory) {
                        enqueueHistoryWrite(updatedState, questionId = result.question.id)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("QuizViewModel", "Error loading question $questionId", e)
                val msg = e.message ?: "Failed to load question"
                _state.update { it.copy(errorMessage = msg) }
                emitSnackbar("Failed to load question: $msg")
            } finally {
                if (isActive && activeDatabaseHolder.activeDatabase.value === active) {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
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

    fun submitAnswer(timeTaken: Long? = null) {
        val currentState = state.value
        val question = currentState.currentQuestion ?: return
        val selectedAnswerId = currentState.selectedAnswerId

        if (selectedAnswerId == null) {
            emitSnackbar("Please select an answer")
            return
        }

        if (currentState.answerSubmitted) return

        val active = activeDatabaseHolder.activeDatabase.value
        val isLoggingEnabled = currentState.isLoggingEnabled
        val correctAnswerId = currentState.correctAnswerId
        val targetSessionId = sessionId
        val elapsedSeconds = timeTaken ?: (questionDisplayedMark?.elapsedNow()?.inWholeSeconds ?: 0L).coerceAtLeast(0L)

        if (isLoggingEnabled && active == null) {
            emitSnackbar("Database not available — answer not saved")
            return
        }

        val wasAlreadySubmitted = _state.getAndUpdate { it.copy(answerSubmitted = true) }.answerSubmitted
        if (wasAlreadySubmitted) return

        if (!isLoggingEnabled) return

        // A question whose answer key can't be derived (malformed bank
        // row) must not reach the log: a corrAnswer sentinel would grade
        // the attempt permanently incorrect in every stat query.
        if (correctAnswerId == null) {
            emitSnackbar("Question has no valid answer key — result not saved")
            return
        }

        val db = active!!.provider
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    db.logAnswer(
                        qid = question.id,
                        selectedAnswer = selectedAnswerId,
                        corrAnswer = correctAnswerId,
                        time = elapsedSeconds,
                        sessionId = targetSessionId,
                    )
                }
                // Only merge performance into the UI while the user is still on the
                // same question of the same database — otherwise the stats would be
                // attributed to whatever is now displayed.
                val stillOnSameQuestion =
                    activeDatabaseHolder.activeDatabase.value === active &&
                        state.value.currentQuestion?.id == question.id
                if (stillOnSameQuestion) {
                    updatePerformanceState(question.id, correctAnswerId, selectedAnswerId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activeDatabaseHolder.activeDatabase.value === active) {
                    _state.update { current ->
                        if (current.currentQuestion?.id == question.id) {
                            current.copy(answerSubmitted = false)
                        } else {
                            current
                        }
                    }
                }
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
        _state.update { current ->
            if (current.currentQuestion?.id != questionId) return@update current
            val previous = current.currentPerformance
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
            current.copy(currentPerformance = updated)
        }
    }

    fun retry() {
        val s = state.value
        if (s.questionIds.isNotEmpty()) {
            loadQuestion(s.currentQuestionIndex.coerceIn(0, s.questionIds.lastIndex))
        } else {
            val active = activeDatabaseHolder.activeDatabase.value
            val expected = expectedDatabaseName
            if (active == null || (lastLoadedDbName == null && !expected.isNullOrBlank() && canonicalizeDbName(active.name) != expected)) {
                val failure = activeDatabaseHolder.openFailure.value
                if (failure != null && (expected == null || failure.dbName == expected)) {
                    _state.update { it.copy(isLoading = false, errorMessage = failure.message) }
                }
                return
            }
            loadFilteredQuestionIds(active = active, startFromBeginning = false)
        }
    }

    private fun loadFilteredQuestionIds(
        active: ActiveDatabase,
        startFromBeginning: Boolean = false,
    ) {
        loadQuestionJob?.cancel()
        loadQuestionJob = null
        filteredIdsJob?.cancel()
        filteredIdsJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val db = active.provider
            val canonicalDbName = canonicalizeDbName(active.name)
            try {
                if (!startFromBeginning && sessionId.isNotBlank() && savedStateHandle.get<Boolean>(KEY_FILTER_INITIALIZED) != true) {
                    val restoredSession = withContext(ioDispatcher) {
                        sessionRepository.restoreHistoryEntry(sessionId)
                    }
                    if (activeDatabaseHolder.activeDatabase.value !== active) {
                        return@launch
                    }
                    if (restoredSession != null) {
                        sessionFilter = QuestionFilter.create(
                            selectedSubjectIds = restoredSession.selectedSubjectIds.toSet(),
                            excludedSubjectIds = restoredSession.excludedSubjectIds.toSet(),
                            selectedSystemIds = restoredSession.selectedSystemIds.toSet(),
                            excludedSystemIds = restoredSession.excludedSystemIds.toSet(),
                            statusFilter = restoredSession.statusFilter,
                            selectedDifficultyTiers = restoredSession.selectedDifficultyTiers,
                        )
                        if (currentQuestionId == null && restoredSession.currentQuestionId != null) {
                            currentQuestionId = restoredSession.currentQuestionId
                            savedStateHandle[KEY_CURRENT_QUESTION_ID] = restoredSession.currentQuestionId
                        }
                    } else {
                        sessionFilter = filterStateHolder.filter.value
                    }
                    saveFilterToSavedState(sessionFilter)
                }

                val filterSnapshot = sessionFilter
                val currentState = state.value
                val (markedIds, ids) = withContext(ioDispatcher) {
                    val marks = try {
                        markedQuestionsRepository.getMarkedQuestionIds(canonicalDbName)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.e("QuizViewModel", "Error loading marked question ids", e)
                        emptySet()
                    }
                    val questionIds = db.getQuestionIds(
                        subjectIds = filterSnapshot.selectedSubjectIds.toList(),
                        excludedSubjectIds = filterSnapshot.excludedSubjectIds.toList(),
                        systemIds = filterSnapshot.selectedSystemIds.toList(),
                        excludedSystemIds = filterSnapshot.excludedSystemIds.toList(),
                        statusFilter = filterSnapshot.statusFilter,
                        difficultyFilters = filterSnapshot.selectedDifficultyTiers,
                    )
                    marks to questionIds
                }

                // Database switched mid-query — a newer load for the new database is
                // responsible for publishing state.
                if (activeDatabaseHolder.activeDatabase.value !== active) {
                    return@launch
                }

                if (ids.isEmpty()) {
                    questionDisplayedMark = null
                    _state.update {
                        it.copy(
                            questionIds = emptyList(),
                            currentQuestionIndex = -1,
                            currentQuestion = null,
                            currentAnswers = emptyList(),
                            selectedAnswerId = null,
                            correctAnswerId = null,
                            questionHighlights = emptyList(),
                            explanationHighlights = emptyList(),
                            currentPerformance = null,
                            answerSubmitted = false,
                            isLoading = false,
                            errorMessage = null,
                            isCurrentQuestionMarked = false,
                            markedQuestionIds = emptySet(),
                        )
                    }
                    return@launch
                }

                val newIndex = if (startFromBeginning) {
                    0
                } else {
                    val matchedIndex = currentQuestionId?.let { targetId -> ids.indexOf(targetId) }?.takeIf { it >= 0 }
                    matchedIndex ?: currentState.currentQuestionIndex.coerceIn(0, ids.lastIndex)
                }
                _state.update {
                    it.copy(
                        questionIds = ids,
                        currentQuestionIndex = newIndex,
                        markedQuestionIds = markedIds,
                    )
                }
                loadQuestion(newIndex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activeDatabaseHolder.activeDatabase.value === active) {
                    val msg = e.message ?: "Failed to load questions from database"
                    _state.update { it.copy(isLoading = false, errorMessage = msg) }
                }
                Logger.e("QuizViewModel", "Error loading filtered questions", e)
            }
        }
    }

    fun clearCurrentQuestionLog() {
        val questionId = _state.value.currentQuestion?.id ?: return
        val active = activeDatabaseHolder.activeDatabase.value
        if (active == null) {
            emitSnackbar("Database not available")
            return
        }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    active.provider.clearLogForQuestion(questionId)
                }
                if (activeDatabaseHolder.activeDatabase.value !== active) {
                    return@launch
                }
                _state.update { current ->
                    if (current.currentQuestion?.id == questionId) {
                        current.copy(currentPerformance = null)
                    } else {
                        current
                    }
                }
                emitSnackbar("Log cleared for current question")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitSnackbar("Failed to clear log: ${e.message}")
            }
        }
    }

    fun toggleMark() {
        val snapshot = state.value
        val dbName = snapshot.databaseName
        val questionId = snapshot.currentQuestion?.id ?: return
        if (dbName.isBlank()) return

        val previousMarked = snapshot.isCurrentQuestionMarked
        val targetMarked = !previousMarked
        val key = dbName to questionId
        val seq = ++markMutationSeq
        latestMarkSeqByQuestion[key] = seq

        // 1. Optimistic UI update (0ms latency)
        _state.update { current ->
            if (current.databaseName != dbName || current.currentQuestion?.id != questionId) {
                current
            } else {
                current.copy(
                    isCurrentQuestionMarked = targetMarked,
                    markedQuestionIds = if (targetMarked) {
                        current.markedQuestionIds + questionId
                    } else {
                        current.markedQuestionIds - questionId
                    }
                )
            }
        }

        // 2. Serialized persistence — Main owns map access, IO performs DB write
        viewModelScope.launch {
            markWriteMutex.withLock {
                // Drop superseded writes before hitting SQLite
                if (latestMarkSeqByQuestion[key] != seq) return@withLock
                try {
                    val persisted = withContext(ioDispatcher) {
                        markedQuestionsRepository.setMarked(dbName, questionId, targetMarked)
                    }
                    if (latestMarkSeqByQuestion[key] != seq) return@withLock
                    _state.update { current ->
                        if (current.databaseName != dbName) return@update current
                        val updatedIds = if (persisted) {
                            current.markedQuestionIds + questionId
                        } else {
                            current.markedQuestionIds - questionId
                        }
                        current.copy(
                            markedQuestionIds = updatedIds,
                            isCurrentQuestionMarked = if (current.currentQuestion?.id == questionId) {
                                persisted
                            } else {
                                current.isCurrentQuestionMarked
                            }
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e("QuizViewModel", "Error updating mark for question $questionId", e)
                    if (latestMarkSeqByQuestion[key] == seq) {
                        val fallbackMarked = try {
                            withContext(ioDispatcher) {
                                markedQuestionsRepository.isMarked(dbName, questionId)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            previousMarked
                        }
                        _state.update { current ->
                            if (current.databaseName != dbName) return@update current
                            val revertedIds = if (fallbackMarked) {
                                current.markedQuestionIds + questionId
                            } else {
                                current.markedQuestionIds - questionId
                            }
                            current.copy(
                                markedQuestionIds = revertedIds,
                                isCurrentQuestionMarked = if (current.currentQuestion?.id == questionId) {
                                    fallbackMarked
                                } else {
                                    current.isCurrentQuestionMarked
                                }
                            )
                        }
                    }
                    emitSnackbar("Failed to update marked status")
                }
            }
        }
    }

    private fun observeSettings() {
        combine(
            settingsRepository.showMetadata,
            settingsRepository.fontScalePreference,
            settingsRepository.isLoggingEnabled,
            settingsRepository.submissionMode,
        ) { metadata, fontScale, isLoggingEnabled, submissionMode ->
            QuizSettingsSnapshot(metadata, fontScale, isLoggingEnabled, submissionMode)
        }
            .distinctUntilChanged()
            .onEach { settings ->
                var sessionSettingsChanged = false
                val updated = _state.updateAndGet { current ->
                    val nextLogging = if (isLoggingSettingLocked) current.isLoggingEnabled else settings.isLoggingEnabled
                    val nextMode = if (isSubmissionModeLocked) current.submissionMode else settings.submissionMode
                    if (nextLogging != current.isLoggingEnabled || nextMode != current.submissionMode) {
                        sessionSettingsChanged = true
                    }
                    current.copy(
                        showMetadata = settings.metadata,
                        fontScalePreference = settings.fontScale,
                        isLoggingEnabled = nextLogging,
                        submissionMode = nextMode,
                    )
                }
                if (sessionSettingsChanged) {
                    persistStateSnapshot(updated)
                    if (sessionId.isNotBlank() && updated.currentQuestion != null) {
                        enqueueHistoryWrite(updated, questionId = updated.currentQuestion.id)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private data class QuizSettingsSnapshot(
        val metadata: Boolean,
        val fontScale: Float?,
        val isLoggingEnabled: Boolean,
        val submissionMode: SubmissionMode,
    )

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
