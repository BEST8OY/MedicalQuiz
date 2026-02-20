package com.medicalquiz.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicalquiz.app.shared.data.CacheManager
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.data.SettingsRepository
import com.medicalquiz.app.shared.data.TextHighlightsRepository
import com.medicalquiz.app.shared.data.database.DatabaseProvider
import com.medicalquiz.app.shared.platform.Logger
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.database.QuestionPerformance
import com.medicalquiz.app.shared.ui.state.QuizUiState
import com.medicalquiz.app.shared.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

private const val MAX_SCROLL_CACHE_SIZE = 100

class QuizViewModel(
    internal val settingsRepository: SettingsRepository,
    private val textHighlightsRepository: TextHighlightsRepository,
    private val cacheManager: CacheManager,
    private val sessionRepository: QuizSessionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {


    private companion object {
        const val KEY_DATABASE_NAME = "database_name"
        const val KEY_SELECTED_SUBJECT_IDS = "selected_subject_ids"
        const val KEY_SELECTED_SYSTEM_IDS = "selected_system_ids"
        const val KEY_PERFORMANCE_FILTER = "performance_filter"
        const val KEY_CURRENT_QUESTION_INDEX = "current_question_index"
    }
    enum class SessionRestoreResult {
        Restored,
        NoSession,
        DatabaseMismatch,
    }

    private var databaseManager: DatabaseProvider? = null
    private var settingsObservationJob: Job? = null

    private var testId = Random.nextLong().toString()

    private val _state = MutableStateFlow(QuizUiState.EMPTY)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    val toolbarTitle = state
        .map { it.databaseName }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ""
        )

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val uiEvents = _uiEvents.asSharedFlow()

    private var lastFetchedSubjectIds: List<Long>? = null
    
    // Scroll position tracking per question - LRU cache with max size
    private val scrollPositionCache = object : LinkedHashMap<Long, Int>(MAX_SCROLL_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Int>?): Boolean {
            return size > MAX_SCROLL_CACHE_SIZE
        }
    }

    init {
        restoreFromSavedState()
        settingsObservationJob = observeSettings(settingsRepository)
    }

    private fun restoreFromSavedState() {
        val savedDatabaseName = savedStateHandle.get<String>(KEY_DATABASE_NAME).orEmpty()
        val savedSubjectIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_SUBJECT_IDS).orEmpty()
        val savedSystemIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_SYSTEM_IDS).orEmpty()
        val savedPerformanceName = savedStateHandle.get<String>(KEY_PERFORMANCE_FILTER)
        val savedQuestionIndex = savedStateHandle.get<Int>(KEY_CURRENT_QUESTION_INDEX) ?: 0

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
            )
        }
    }

    private fun persistStateSnapshot(snapshot: QuizUiState = state.value) {
        savedStateHandle[KEY_DATABASE_NAME] = snapshot.databaseName
        savedStateHandle[KEY_SELECTED_SUBJECT_IDS] = snapshot.selectedSubjectIds.toList()
        savedStateHandle[KEY_SELECTED_SYSTEM_IDS] = snapshot.selectedSystemIds.toList()
        savedStateHandle[KEY_PERFORMANCE_FILTER] = snapshot.performanceFilter.name
        savedStateHandle[KEY_CURRENT_QUESTION_INDEX] = snapshot.currentQuestionIndex
    }

    suspend fun setDatabaseManager(db: DatabaseProvider) {
        // Reset state immediately so UI clears old data.
        // This function is suspend so callers can await full initialization
        // before restoring a saved session/history entry.
        resetState()

        val oldDb = databaseManager
        databaseManager = db

        try {
            withContext(Dispatchers.IO) {
                oldDb?.closeDatabase()
            }
        } catch (e: Exception) {
            Logger.e("QuizViewModel", "Error closing old database", e)
        }

        initializeAfterDatabaseSwitch()
    }

    private fun resetState() {
        _state.update { currentState ->
            // Reset to empty state but preserve settings that shouldn't change
            QuizUiState.EMPTY.copy(
                isLoggingEnabled = currentState.isLoggingEnabled,
                showMetadata = currentState.showMetadata,
                databaseName = "" // Will be set shortly after
            )
        }
        scrollPositionCache.clear()
        persistStateSnapshot()
    }

    fun getTextHighlightsRepository(): TextHighlightsRepository = textHighlightsRepository

    /**
     * Restores quiz session state from the repository.
     * Should be called after database is initialized.
     *
     * @return restore outcome for session availability and database compatibility
     */
    fun restoreSession(): SessionRestoreResult {
        val session = sessionRepository.restoreSession() ?: return SessionRestoreResult.NoSession
        val currentState = state.value

        // Only restore if the database matches
        if (session.databaseName != currentState.databaseName) {
            return SessionRestoreResult.DatabaseMismatch
        }

        _state.update {
            it.copy(
                selectedSubjectIds = session.selectedSubjectIds.toSet(),
                selectedSystemIds = session.selectedSystemIds.toSet(),
                performanceFilter = session.performanceFilter,
                currentQuestionIndex = session.currentQuestionIndex
            )
        }
        persistStateSnapshot()
        return SessionRestoreResult.Restored
    }

    private fun saveSession(appendToHistory: Boolean = true) {
        val currentState = state.value
        sessionRepository.saveSession(
            databaseName = currentState.databaseName,
            selectedSubjectIds = currentState.selectedSubjectIds,
            selectedSystemIds = currentState.selectedSystemIds,
            performanceFilter = currentState.performanceFilter,
            currentQuestionIndex = currentState.currentQuestionIndex,
            appendToHistory = appendToHistory
        )
    }

    /**
     * Clears the saved quiz session.
     * Call this when the user intentionally exits the quiz (e.g., navigates back to filter).
     */
    fun clearSession() {
        sessionRepository.clearSession()
    }

    fun closeDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                databaseManager?.closeDatabase()
            } catch (e: Exception) {
                emitToast("Error closing database: ${e.message}")
            }
        }
        // Clear the session when database is explicitly closed (e.g., switching databases)
        sessionRepository.clearSession()
    }

    private suspend fun initializeAfterDatabaseSwitch() {
        try {
            Logger.d("QuizViewModel", "Initializing after database switch")
            
            // Start fresh with new database
            _state.update {
                it.copy(
                    selectedSubjectIds = emptySet(),
                    selectedSystemIds = emptySet(),
                    questionIds = emptyList(),
                    performanceFilter = PerformanceFilter.ALL,
                    previewQuestionCount = 0
                )
            }

            lastFetchedSubjectIds = null

            fetchSubjects()
            fetchSystemsForSubjects(null)
            updatePreviewQuestionCountInternal()
            // No subjects selected initially, so no systems to fetch
            Logger.d("QuizViewModel", "Database initialization completed")
        } catch (e: Exception) {
            Logger.e("QuizViewModel", "Error during post-switch initialization", e)
            emitToast("Database initialization incomplete: ${e.message}")
        }
    }

    fun getDatabaseManager(): DatabaseProvider? = databaseManager

    fun setTestId(id: String) {
        testId = id
    }

    fun getTestId(): String = testId

    fun loadQuestion(
        index: Int,
        resetAnswerState: Boolean = true,
        appendToHistory: Boolean = true,
    ) {
        val ids = state.value.questionIds
        val questionId = ids.getOrNull(index) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, currentPerformance = null) }
            try {
                val question = databaseManager?.getQuestionById(questionId)
                val answers = databaseManager?.getAnswersForQuestion(questionId) ?: emptyList()
                _state.update { 
                    it.copy(currentQuestionIndex = index)
                      .copyWithQuestion(
                          question = question,
                          answers = answers,
                          resetAnswerState = resetAnswerState
                      )
                }
                persistStateSnapshot()
                if (question != null) {
                    loadPerformanceForQuestion(question.id)
                    // Load text highlights for the new question
                    textHighlightsRepository.loadHighlightsForQuestion(question.id)
                }
            } catch (e: Exception) {
                Logger.e("QuizViewModel", "Error loading question $questionId", e)
                emitToast("Failed to load question: ${e.message}")
            } finally {
                _state.update { it.copy(isLoading = false) }
                cacheManager?.trimCachesIfNeeded(index)
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
            emitToast("Please select an answer")
            return
        }

        if (currentState.answerSubmitted) return

        _state.update { it.copy(answerSubmitted = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isLoggingEnabled = settingsRepository.isLoggingEnabled.value
                
                if (isLoggingEnabled) {
                    val correctAnswer = currentState.currentAnswers.getOrNull(question.corrAns - 1)
                    val correctAnswerId = correctAnswer?.answerId?.toInt() ?: -1
                    logAnswerToDatabase(question.id, selectedAnswerId, correctAnswerId, timeTaken)
                    updatePerformanceState(question.id, correctAnswerId, selectedAnswerId)
                }
            } catch (e: Exception) {
                _state.update { it.copy(answerSubmitted = false) }
                emitToast("Error saving answer: ${e.message}")
            }
        }
    }

    private suspend fun logAnswerToDatabase(
        questionId: Long,
        selectedAnswerId: Int,
        correctAnswerIndex: Int,
        timeTaken: Long
    ) {
        databaseManager?.logAnswer(
            qid = questionId,
            selectedAnswer = selectedAnswerId,
            corrAnswer = correctAnswerIndex,
            time = timeTaken,
            testId = testId
        )
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
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                val currentState = state.value
                val ids = fetchQuestionIdsWithFilters(
                    currentState.selectedSubjectIds.toList(),
                    currentState.selectedSystemIds.toList(),
                    currentState.performanceFilter
                )
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
                    // No quiz was started (no matching questions), so keep active session
                    // state in sync without polluting history.
                    saveSession(appendToHistory = false)
                    return@launch
                }

                val newIndex = currentState.currentQuestionIndex.coerceIn(0, ids.lastIndex)
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

    suspend fun fetchFilteredQuestionIds(): List<Long> {
        val currentState = state.value
        return fetchQuestionIdsWithFilters(
            currentState.selectedSubjectIds.toList(),
            currentState.selectedSystemIds.toList(),
            currentState.performanceFilter
        )
    }

    private suspend fun fetchQuestionIdsWithFilters(
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter
    ): List<Long> {
        return databaseManager?.getQuestionIds(
            subjectIds = subjectIds,
            systemIds = systemIds,
            performanceFilter = performanceFilter
        ) ?: emptyList()
    }

    fun applySelectedSubjects(newSubjectIds: Set<Long>, loadQuestions: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val previouslySelectedSystems = state.value.selectedSystemIds
            _state.update { it.copy(selectedSubjectIds = newSubjectIds) }
            persistStateSnapshot()

            val validSystems = if (newSubjectIds.isEmpty()) {
                emptySet()
            } else {
                databaseManager?.getSystems(newSubjectIds.toList())
                    ?.map { it.id }
                    ?.toSet() ?: emptySet()
            }

            val prunedSelectedSystems = previouslySelectedSystems.intersect(validSystems)
            _state.update { it.copy(selectedSystemIds = prunedSelectedSystems) }
            persistStateSnapshot()

            val subjectsForSystems = newSubjectIds
                .takeIf { it.isNotEmpty() }
                ?.toList()
            fetchSystemsForSubjects(subjectsForSystems)

            if (loadQuestions) {
                // loadFilteredQuestionIds will update previewQuestionCount
                loadFilteredQuestionIds(updatePreviewCount = true, appendToHistory = false)
            } else {
                updatePreviewQuestionCountInternal()
            }
            saveSession(appendToHistory = false)
        }
    }

    fun fetchSubjects() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(subjectsResource = Resource.Loading) }
            try {
                val subjects = databaseManager?.getSubjects() ?: emptyList()
                _state.update { it.copy(subjectsResource = Resource.Success(subjects)) }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error"
                _state.update { it.copy(subjectsResource = Resource.Error(errorMessage)) }
                emitToast("Error fetching subjects: $errorMessage")
            }
        }
    }

    private suspend fun pruneInvalidSubjects(): List<Long> {
        val db = databaseManager ?: return emptyList()
        val available = db.getSubjects().map { it.id }
        return state.value.selectedSubjectIds.filter { it in available }
    }

    fun applySelectedSystems(newSystemIds: Set<Long>, loadQuestions: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val validSystems = pruneInvalidSystems()
            val normalizedSelection = if (validSystems.isEmpty()) {
                emptySet()
            } else {
                newSystemIds.intersect(validSystems)
            }

            _state.update { it.copy(selectedSystemIds = normalizedSelection) }
            persistStateSnapshot()
            updatePreviewQuestionCountInternal()
            if (loadQuestions) {
                loadFilteredQuestionIds(appendToHistory = false)
            }
            saveSession(appendToHistory = false)
        }
    }

    fun fetchSystemsForSubjects(subjectIds: List<Long>?) {
        if (shouldSkipSystemFetch(subjectIds)) return
        
        lastFetchedSubjectIds = subjectIds?.toList() ?: emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(systemsResource = Resource.Loading) }
            try {
                val systems = databaseManager?.getSystems(subjectIds) ?: emptyList()
                _state.update { it.copy(systemsResource = Resource.Success(systems)) }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error"
                _state.update { it.copy(systemsResource = Resource.Error(errorMessage)) }
                emitToast("Error fetching systems: $errorMessage")
            }
        }
    }

    private fun shouldSkipSystemFetch(subjectIds: List<Long>?): Boolean {
        val lastFetched = lastFetchedSubjectIds ?: return false
        val normalizedRequested = subjectIds?.toSet() ?: emptySet()
        return lastFetched.toSet() == normalizedRequested
    }

    private suspend fun pruneInvalidSystems(): Set<Long> {
        val db = databaseManager ?: return emptySet()
        val subjects = state.value.selectedSubjectIds.toList()
        val availableSystems = if (subjects.isEmpty()) {
            db.getSystems(null)
        } else {
            db.getSystems(subjects)
        }
        return availableSystems.map { it.id }.toSet()
    }

    fun setPerformanceFilter(filter: PerformanceFilter, loadQuestions: Boolean = true) {
        _state.update { it.copy(performanceFilter = filter) }
        persistStateSnapshot()
        viewModelScope.launch(Dispatchers.IO) {
            if (loadQuestions) {
                // loadFilteredQuestionIds will update previewQuestionCount
                loadFilteredQuestionIds(updatePreviewCount = true, appendToHistory = false)
            } else {
                updatePreviewQuestionCountInternal()
            }
            saveSession(appendToHistory = false)
        }
    }

    fun navigateToDatabaseSelection() {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.NavigateToDatabaseSelection)
        }
    }

    fun setDatabaseName(name: String) {
        _state.update { it.copy(databaseName = name) }
        persistStateSnapshot()
        // Notify text highlights repository of database switch
        textHighlightsRepository.setCurrentDatabase(name)

        // If a question was already loaded before database name propagation finished,
        // reload highlights now that repository context is guaranteed.
        state.value.currentQuestion?.id?.let { questionId ->
            textHighlightsRepository.loadHighlightsForQuestion(questionId)
        }
    }

    private suspend fun updatePreviewQuestionCountInternal() {
        try {
            val count = fetchFilteredQuestionIds().size
            _state.update { it.copy(previewQuestionCount = count) }
        } catch (e: Exception) {
            _state.update { it.copy(previewQuestionCount = 0) }
        }
    }

    fun loadPerformanceForQuestion(questionId: Long) {
        val isLoggingEnabled = settingsRepository.isLoggingEnabled.value
        
        if (!isLoggingEnabled) {
            _state.update { it.copy(currentPerformance = null) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val performance = databaseManager?.getQuestionPerformance(questionId)
                _state.update { it.copy(currentPerformance = performance) }
            } catch (e: Exception) {
                _state.update { it.copy(currentPerformance = null) }
                emitToast("Unable to load performance for question $questionId")
            }
        }
    }

    fun clearLogsFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                databaseManager?.clearLogs()
                emitToast("Logs cleared")
            } catch (e: Exception) {
                emitToast("Failed to clear logs: ${e.message}")
            }
        }
    }

    fun clearCurrentQuestionLog() {
        val questionId = _state.value.currentQuestion?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                databaseManager?.clearLogForQuestion(questionId)
                _state.update { it.copy(currentPerformance = null) }
                emitToast("Log cleared for current question")
            } catch (e: Exception) {
                emitToast("Failed to clear log: ${e.message}")
            }
        }
    }

    fun openMedia(urls: List<String>, startIndex: Int) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.OpenMedia(urls, startIndex))
        }
    }
    
    fun openHtmlFile(fileName: String) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.OpenHtmlFile(fileName))
        }
    }

    private fun observeSettings(repo: SettingsRepository): Job {
        return viewModelScope.launch {
            launch {
                repo.isLoggingEnabled.collect { enabled ->
                    _state.update { it.copy(isLoggingEnabled = enabled) }
                }
            }
            launch {
                repo.showMetadata.collect { visible ->
                    _state.update { it.copy(showMetadata = visible) }
                }
            }
        }
    }

    private fun emitToast(message: String) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast(message))
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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { databaseManager?.closeDatabase() }
        }
        super.onCleared()
    }
}
