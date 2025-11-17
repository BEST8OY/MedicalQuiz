package com.medicalquiz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicalquiz.app.MedicalQuizApp
import com.medicalquiz.app.data.CacheManager
import com.medicalquiz.app.data.SettingsRepository
import com.medicalquiz.app.data.database.DatabaseProvider
import com.medicalquiz.app.data.database.PerformanceFilter
import com.medicalquiz.app.data.database.QuestionPerformance
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.ui.QuizState
import com.medicalquiz.app.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

/**
 * ViewModel that manages quiz state and coordinates database operations.
 * Serves as the single source of truth for quiz data, keeping it safe across lifecycle changes.
 */
class QuizViewModel : ViewModel() {

    // ============================================================================
    // Constants
    // ============================================================================

    // NOTE: 'const val' cannot be declared inside a class body in Kotlin unless it
    // is inside a 'companion object' — we declare this value in the companion
    // object below to allow the compiler to treat it as a compile-time constant.

    // ============================================================================
    // Dependencies
    // ============================================================================

    private var databaseManager: DatabaseProvider? = null
    private var settingsRepository: SettingsRepository? = null
    private var cacheManager: CacheManager? = null
    private var settingsObservationJob: kotlinx.coroutines.Job? = null

    // ============================================================================
    // State Management
    // ============================================================================

    private var testId = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(QuizState.EMPTY)
    val state: StateFlow<QuizState> = _state.asStateFlow()

    // Derived flows for UI convenience
    val toolbarTitle = state.map { it.databaseName }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ""
    )

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = UI_EVENTS_BUFFER_CAPACITY)
    val uiEvents = _uiEvents.asSharedFlow()

    private var lastFetchedSubjectIds: List<Long>? = null

    // ============================================================================
    // Dependency Injection
    // ============================================================================

    fun setDatabaseManager(db: DatabaseProvider) {
        databaseManager = db
        viewModelScope.launch(Dispatchers.IO) {
            initializeAfterDatabaseSwitch()
        }
    }

    companion object {
        private const val UI_EVENTS_BUFFER_CAPACITY = 4
        private const val TAG = "QuizViewModel"
    }

    fun setSettingsRepository(repo: SettingsRepository) {
        if (settingsRepository === repo) return
        settingsRepository = repo
        settingsObservationJob?.cancel()
        settingsObservationJob = observeSettings(repo)
    }

    fun setCacheManager(cache: CacheManager) {
        cacheManager = cache
    }

    // ============================================================================
    // Database Management
    // ============================================================================

    fun switchDatabase(newDbPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Switching database to: $newDbPath")
                val db = MedicalQuizApp.switchDatabase(newDbPath)
                Log.d(TAG, "Database switched successfully")
                setDatabaseManager(db)
                Log.d(TAG, "Database manager set and initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch database", e)
                emitToast("Failed to switch database: ${e.message}")
            }
        }
    }

    fun initializeDatabase(dbPath: String) {
        switchDatabase(dbPath)
    }

    fun closeDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                databaseManager?.closeDatabase()
            } catch (e: Exception) {
                emitToast("Error closing database: ${e.message}")
            }
        }
    }

    private suspend fun initializeAfterDatabaseSwitch() {
        try {
            Log.d(TAG, "Initializing after database switch")
            val validSubjects = pruneInvalidSubjects().toSet()
            val validSystems = if (validSubjects.isEmpty()) {
                emptySet()
            } else {
                pruneInvalidSystems()
            }

            Log.d(TAG, "Valid subjects: ${validSubjects.size}, Valid systems: ${validSystems.size}")

            _state.update {
                it.copy(
                    selectedSubjectIds = validSubjects,
                    selectedSystemIds = validSystems,
                    questionIds = emptyList()
                )
            }

            Log.d(TAG, "State updated: questionIds is now empty, filters should display")

            lastFetchedSubjectIds = null

            // Prefetch metadata for snappy UI interactions
            fetchSubjects()
            fetchSystemsForSubjects(validSubjects.takeIf { it.isNotEmpty() }?.toList())
            Log.d(TAG, "Database initialization completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during post-switch initialization", e)
            emitToast("Database initialization incomplete: ${e.message}")
        }
    }

    fun getDatabaseManager(): DatabaseProvider? = databaseManager

    // ============================================================================
    // Test Management
    // ============================================================================

    fun setTestId(id: String) {
        testId = id
    }

    fun getTestId(): String = testId

    // ============================================================================
    // Question Navigation
    // ============================================================================

    fun loadQuestion(index: Int) {
        val ids = state.value.questionIds
        val questionId = ids.getOrNull(index) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                val question = databaseManager?.getQuestionById(questionId)
                val answers = databaseManager?.getAnswersForQuestion(questionId) ?: emptyList()
                _state.update { 
                    it.copy(currentQuestionIndex = index)
                      .copyWithQuestion(question, answers)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading question $questionId", e)
                emitToast("Failed to load question: ${e.message}")
            } finally {
                _state.update { it.copy(isLoading = false) }
                cacheManager?.trimCachesIfNeeded(index)
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

    fun restoreQuestionFromDatabase(questionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val question = databaseManager?.getQuestionById(questionId)
                val answers = databaseManager?.getAnswersForQuestion(questionId) ?: emptyList()
                val ids = databaseManager?.getQuestionIds() ?: emptyList()
                val index = ids.indexOf(questionId).coerceAtLeast(0)

                _state.update {
                    it.copy(questionIds = ids, currentQuestionIndex = index)
                        .copyWithQuestion(question, answers)
                }
            } catch (e: Exception) {
                emitToast("Error restoring question: ${e.message}")
            }
        }
    }

    // ============================================================================
    // Answer Management
    // ============================================================================

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
                val isLoggingEnabled = settingsRepository?.isLoggingEnabled?.value ?: true
                val correctAnswer = currentState.currentAnswers.getOrNull(question.corrAns - 1)
                val correctAnswerId = correctAnswer?.answerId?.toInt() ?: -1

                if (isLoggingEnabled) {
                    logAnswerToDatabase(question.id, selectedAnswerId, question.corrAns, timeTaken)
                    updatePerformanceState(question.id, correctAnswerId, selectedAnswerId)
                }

                _uiEvents.emit(UiEvent.ShowAnswer(correctAnswerId, selectedAnswerId))
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
                attempts = previous.attempts + 1
            )
        } else {
            QuestionPerformance(
                qid = questionId,
                lastCorrect = wasCorrect,
                everCorrect = wasCorrect,
                everIncorrect = !wasCorrect,
                attempts = 1
            )
        }

        _state.update { it.copy(currentPerformance = updated) }
    }

    fun resetAnswerState() {
        _state.update { it.copy(selectedAnswerId = null, answerSubmitted = false) }
    }

    fun setAnswerSubmissionState(submitted: Boolean, selectedAnswerId: Int?) {
        _state.update { it.copy(answerSubmitted = submitted, selectedAnswerId = selectedAnswerId) }
    }

    // ============================================================================
    // Filter Management - Question IDs
    // ============================================================================

    fun setQuestionIds(ids: List<Long>) {
        Log.d("QuizViewModel", "Setting question IDs: ${ids.size} questions")
        _state.update { it.copy(questionIds = ids) }
    }

    fun requestAutoLoadFirstQuestion() {
        _state.update { it.copy(autoLoadFirstQuestion = true) }
    }

    fun clearAutoLoadFirstQuestion() {
        _state.update { it.copy(autoLoadFirstQuestion = false) }
    }

    fun loadFilteredQuestionIds() {
        Log.d(TAG, "Loading filtered question IDs")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentState = state.value
                Log.d(TAG, "Filters: ${currentState.selectedSubjectIds.size} subjects, ${currentState.selectedSystemIds.size} systems, ${currentState.performanceFilter}")
                val ids = fetchQuestionIdsWithFilters(
                    currentState.selectedSubjectIds.toList(),
                    currentState.selectedSystemIds.toList(),
                    currentState.performanceFilter
                )
                Log.d(TAG, "Filtered query returned ${ids.size} questions")
                // Only update if filters haven't changed since we started the fetch
                _state.update { latestState ->
                    if (latestState.selectedSubjectIds == currentState.selectedSubjectIds &&
                        latestState.selectedSystemIds == currentState.selectedSystemIds &&
                        latestState.performanceFilter == currentState.performanceFilter) {
                        latestState.copy(questionIds = ids)
                    } else {
                        Log.d(TAG, "Filters changed during fetch, discarding stale results")
                        latestState
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading filtered questions", e)
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

    // ============================================================================
    // Filter Management - Subjects
    // ============================================================================

    fun setSelectedSubjects(ids: Set<Long>) {
        applySelectedSubjects(ids)
    }

    fun setSelectedSubjectsSilently(ids: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(selectedSubjectIds = ids) }
            val validSystems = if (ids.isEmpty()) {
                emptySet()
            } else {
                pruneInvalidSystems().toSet()
            }
            _state.update { it.copy(selectedSystemIds = validSystems) }
        }
    }

    fun applySelectedSubjects(newSubjectIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(selectedSubjectIds = newSubjectIds) }
            
            val validSystems = if (newSubjectIds.isEmpty()) {
                emptySet()
            } else {
                databaseManager?.getSystems(newSubjectIds.toList())
                    ?.map { it.id }
                    ?.toSet() ?: emptySet()
            }
            
            _state.update { it.copy(selectedSystemIds = validSystems) }
            // Update preview count
            updatePreviewQuestionCount()
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

    suspend fun getSubjects(): List<Subject> {
        return databaseManager?.getSubjects() ?: emptyList()
    }

    private suspend fun pruneInvalidSubjects(): List<Long> {
        val db = databaseManager ?: return emptyList()
        val available = db.getSubjects().map { it.id }
        return state.value.selectedSubjectIds.filter { it in available }
    }

    // ============================================================================
    // Filter Management - Systems
    // ============================================================================

    fun setSelectedSystems(ids: Set<Long>) {
        applySelectedSystems(ids)
    }

    fun setSelectedSystemsSilently(ids: Set<Long>) {
        _state.update { it.copy(selectedSystemIds = ids) }
    }

    fun applySelectedSystems(newSystemIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(selectedSystemIds = newSystemIds) }
            // Update preview count
            updatePreviewQuestionCount()
        }
    }

    fun fetchSystemsForSubjects(subjectIds: List<Long>?) {
        if (shouldSkipSystemFetch(subjectIds)) return
        
        lastFetchedSubjectIds = subjectIds?.toList()

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
        if (lastFetchedSubjectIds == null) return false
        if (subjectIds == null) return lastFetchedSubjectIds == null
        return lastFetchedSubjectIds!!.toSet() == subjectIds.toSet()
    }

    suspend fun getSystemsForSubjects(subjectIds: List<Long>?): List<System> {
        return databaseManager?.getSystems(subjectIds) ?: emptyList()
    }

    private suspend fun pruneInvalidSystems(): Set<Long> {
        val db = databaseManager ?: return emptySet()
        val subjects = state.value.selectedSubjectIds.toList()
        if (subjects.isEmpty()) return emptySet()
        return db.getSystems(subjects).map { it.id }.toSet()
    }

    // ============================================================================
    // Filter Management - Performance
    // ============================================================================

    fun setPerformanceFilter(filter: PerformanceFilter) {
        _state.update { it.copy(performanceFilter = filter) }
        loadFilteredQuestionIds()
        viewModelScope.launch(Dispatchers.IO) {
            updatePreviewQuestionCount()
        }
    }

    fun openPerformanceDialog() {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.OpenPerformanceDialog)
        }
    }

    fun setPerformanceFilterSilently(filter: PerformanceFilter) {
        _state.update { it.copy(performanceFilter = filter) }
    }

    fun setDatabaseName(name: String) {
        _state.update { it.copy(databaseName = name) }
    }

    private fun updatePreviewQuestionCount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = fetchFilteredQuestionIds().size
                _state.update { it.copy(previewQuestionCount = count) }
            } catch (e: Exception) {
                _state.update { it.copy(previewQuestionCount = 0) }
            }
        }
    }

    fun loadPerformanceForQuestion(questionId: Long) {
        val isLoggingEnabled = settingsRepository?.isLoggingEnabled?.value ?: true
        
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

    // ============================================================================
    // Logging Management
    // ============================================================================

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

    fun clearPendingLogsBuffer() {
        viewModelScope.launch(Dispatchers.IO) {
            databaseManager?.clearPendingLogsBuffer()
        }
    }

    fun flushLogsIfEnabledOnPause() {
        viewModelScope.launch(Dispatchers.IO) {
            val enabled = settingsRepository?.isLoggingEnabled?.value ?: true
            
            if (enabled) {
                try {
                    val flushed = databaseManager?.flushLogs() ?: 0
                    if (flushed > 0) {
                        emitToast("Flushed $flushed logs")
                    }
                } catch (e: Exception) {
                    emitToast("Failed to flush logs: ${e.message}")
                }
            } else {
                databaseManager?.clearPendingLogsBuffer()
            }
        }
    }

    // ============================================================================
    // Media Management
    // ============================================================================

    fun openMedia(url: String) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.OpenMedia(url))
        }
    }

    // ============================================================================
    // Settings Observation
    // ============================================================================

    private fun observeSettings(repo: SettingsRepository): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            launch {
                repo.isLoggingEnabled.collect { enabled ->
                    _state.update { it.copy(isLoggingEnabled = enabled) }
                }
            }
            // performanceFilter is managed directly by the ViewModel via setPerformanceFilter
            // to avoid dual ownership and redundant state updates
        }
    }

    // ============================================================================
    // Event Emission Helpers
    // ============================================================================

    private fun emitToast(message: String) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast(message))
        }
    }
}