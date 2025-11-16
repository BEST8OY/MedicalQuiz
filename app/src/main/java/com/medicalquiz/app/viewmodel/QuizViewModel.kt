package com.medicalquiz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.medicalquiz.app.data.database.PerformanceFilter
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import com.medicalquiz.app.MedicalQuizApp

/**
 * ViewModel that holds quiz state and performs database operations.
 * Keeps question/answers out of the Activity for lifecycle safety.
 */
class QuizViewModel : ViewModel() {

    private var databaseManager: com.medicalquiz.app.data.database.DatabaseProvider? = null
    private var settingsRepository: com.medicalquiz.app.data.SettingsRepository? = null
    private var cacheManager: com.medicalquiz.app.data.CacheManager? = null
    private var testId = java.util.UUID.randomUUID().toString()

    fun setTestId(id: String) {
        testId = id
    }

    fun getTestId(): String = testId

    private val _state = MutableStateFlow(com.medicalquiz.app.ui.QuizState.EMPTY)
    val state: StateFlow<com.medicalquiz.app.ui.QuizState> = _state.asStateFlow()

    private val _uiEvents = MutableSharedFlow<com.medicalquiz.app.viewmodel.UiEvent>(extraBufferCapacity = 4)
    val uiEvents = _uiEvents.asSharedFlow()

    fun openMedia(url: String) {
        viewModelScope.launch {
            _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.OpenMedia(url))
        }
    }

    fun onAnswerSelected(answerId: Long) {
        // Update selected answer in the single source of truth
        _state.update { it.copy(selectedAnswerId = answerId.toInt()) }
    }

    fun submitAnswer(timeTaken: Long) {
        val question = state.value.currentQuestion ?: return
        val selectedAnswerId = state.value.selectedAnswerId
        if (selectedAnswerId == null) {
            viewModelScope.launch { _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Please select an answer")) }
            return
        }

        // Avoid double submissions
        if (state.value.answerSubmitted) return

        _state.update { it.copy(answerSubmitted = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
            val isLoggingEnabled = settingsRepository?.isLoggingEnabled?.value ?: true
            if (isLoggingEnabled) {
                    val correctAnswer = state.value.currentAnswers.getOrNull(question.corrAns - 1)
                    val normalizedCorrectId = correctAnswer?.answerId?.toInt() ?: -1
                    databaseManager?.logAnswer(
                        qid = question.id,
                        selectedAnswer = selectedAnswerId,
                        corrAnswer = question.corrAns,
                        time = timeTaken,
                        testId = testId
                    )

                    // Update local performance state in SSoT
                    val wasCorrect = normalizedCorrectId == selectedAnswerId
                    val previous = state.value.currentPerformance
                    val updated = previous?.copy(
                        lastCorrect = wasCorrect,
                        everCorrect = previous.everCorrect || wasCorrect,
                        everIncorrect = previous.everIncorrect || !wasCorrect,
                        attempts = previous.attempts + 1
                    ) ?: com.medicalquiz.app.data.database.QuestionPerformance(
                        qid = question.id,
                        lastCorrect = wasCorrect,
                        everCorrect = wasCorrect,
                        everIncorrect = !wasCorrect,
                        attempts = 1
                    )
                    _state.update { it.copy(currentPerformance = updated) }

                    // Notify UI to update the webview answer state
                    _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowAnswer(normalizedCorrectId, selectedAnswerId))
                } else {
                    // Even if logging disabled, show correct answer state in UI
                    val correctAnswer = state.value.currentAnswers.getOrNull(question.corrAns - 1)
                    val normalizedCorrectId = correctAnswer?.answerId?.toInt() ?: -1
                    _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowAnswer(normalizedCorrectId, selectedAnswerId))
                }
            } catch (e: Exception) {
                _state.update { it.copy(answerSubmitted = false) }
                _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Error saving answer: ${e.message}"))
            }
        }
    }

    fun initializeDatabase(dbPath: String) {
        // Initialize DB using the centralized switch method
        switchDatabase(dbPath)
    }

    fun setDatabaseManager(db: com.medicalquiz.app.data.database.DatabaseProvider) {
        databaseManager = db
        viewModelScope.launch(Dispatchers.IO) {
            val ids = databaseManager?.getQuestionIds() ?: emptyList()
            _state.update { it.copy(questionIds = ids) }
        }
    }

    fun switchDatabase(newDbPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = MedicalQuizApp.switchDatabase(newDbPath)
                setDatabaseManager(db)
            } catch (e: Exception) {
                _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Failed to switch database: ${e.message}"))
            }
        }
    }

    fun closeDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                databaseManager?.closeDatabase()
            } catch (e: Exception) {
                _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Error closing database: ${e.message}"))
            }
        }
    }

    fun setSettingsRepository(repo: com.medicalquiz.app.data.SettingsRepository) {
        if (settingsRepository === repo) return
        settingsRepository = repo
        viewModelScope.launch {
            // mirror the settings in the UI state so the Activity can drive visibility and logic from state
            repo.isLoggingEnabled.collect { enabled ->
                _state.update { it.copy(isLoggingEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            repo.performanceFilter.collect { pf ->
                // synchronize the persisted performance filter into the SSoT
                _state.update { it.copy(performanceFilter = pf) }
            }
        }
    }

    fun setCacheManager(cache: com.medicalquiz.app.data.CacheManager) {
        cacheManager = cache
    }

    fun loadQuestion(index: Int) {
        val ids = state.value.questionIds
        val questionId = ids.getOrNull(index) ?: return
        _state.update { it.copy(currentQuestionIndex = index) }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                val q = databaseManager?.getQuestionById(questionId)
                val a = databaseManager?.getAnswersForQuestion(questionId) ?: emptyList()
                _state.update { it.copyWithQuestion(q, a) }
            } finally {
                _state.update { it.copy(isLoading = false) }
                cacheManager?.trimCachesIfNeeded(index)
            }
        }
    }

    fun loadNext() {
        val current = state.value.currentQuestionIndex + 1
        if (current < state.value.questionIds.size) loadQuestion(current)
    }

    fun loadPrevious() {
        val current = state.value.currentQuestionIndex - 1
        if (current >= 0) loadQuestion(current)
    }

    fun setQuestionIds(ids: List<Long>) {
        _state.update { it.copy(questionIds = ids) }
    }

    fun setSelectedSubjects(ids: Set<Long>) {
        _state.update { it.copy(selectedSubjectIds = ids) }
        loadFilteredQuestionIds()
    }

    fun setSelectedSystems(ids: Set<Long>) {
        _state.update { it.copy(selectedSystemIds = ids) }
        loadFilteredQuestionIds()
    }

    fun applySelectedSubjects(newSubjectIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            // Update selected subjects, prune systems and then request filtered IDs
            _state.update { it.copy(selectedSubjectIds = newSubjectIds) }
            val valid = pruneInvalidSystems().toSet()
            _state.update { it.copy(selectedSystemIds = valid) }
            loadFilteredQuestionIds()
        }
    }

    fun applySelectedSystems(newSystemIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(selectedSystemIds = newSystemIds) }
            loadFilteredQuestionIds()
        }
    }

    fun setPerformanceFilter(filter: PerformanceFilter) {
        _state.update { it.copy(performanceFilter = filter) }
        loadFilteredQuestionIds()
    }

    fun loadFilteredQuestionIds() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = state.value.selectedSubjectIds.toList()
            val sys = state.value.selectedSystemIds.toList()
            val pf = state.value.performanceFilter
            val ids = databaseManager?.getQuestionIds(subjectIds = s, systemIds = sys, performanceFilter = pf) ?: emptyList()
            _state.update { it.copy(questionIds = ids) }
        }
    }

    suspend fun pruneInvalidSystems(): List<Long> {
        val subjects = state.value.selectedSubjectIds.toList()
        return databaseManager?.getSystems(subjects)?.map { it.id } ?: emptyList()
    }

    suspend fun fetchFilteredQuestionIds(): List<Long> {
        val s = state.value.selectedSubjectIds.toList()
        val sys = state.value.selectedSystemIds.toList()
        val pf = state.value.performanceFilter
        return databaseManager?.getQuestionIds(subjectIds = s, systemIds = sys, performanceFilter = pf) ?: emptyList()
    }

    suspend fun getSystemsForSubjects(subjectIds: List<Long>?): List<System> {
        return databaseManager?.getSystems(subjectIds) ?: emptyList()
    }

    suspend fun getSubjects(): List<Subject> {
        return databaseManager?.getSubjects() ?: emptyList()
    }

    // systemsResource is now part of the unified QuizState

    private var lastFetchedSubjectIds: List<Long>? = null

    fun fetchSystemsForSubjects(subjectIds: List<Long>?) {
        // Prevent refetch when same subjects requested
        if (subjectIds == lastFetchedSubjectIds) return
        lastFetchedSubjectIds = subjectIds

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(systemsResource = Resource.Loading) }
            try {
                val systems = databaseManager?.getSystems(subjectIds) ?: emptyList()
                _state.update { it.copy(systemsResource = Resource.Success(systems)) }
            } catch (e: Exception) {
                _state.update { it.copy(systemsResource = Resource.Error(e.message ?: "Unknown error")) }
                _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Error fetching systems: ${e.message}"))
            }
        }
    }

    // subjectsResource is now part of the unified QuizState

    fun fetchSubjects() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(subjectsResource = Resource.Loading) }
            try {
                val subs = databaseManager?.getSubjects() ?: emptyList()
                _state.update { it.copy(subjectsResource = Resource.Success(subs)) }
            } catch (e: Exception) {
                _state.update { it.copy(subjectsResource = Resource.Error(e.message ?: "Unknown error")) }
                _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Error fetching subjects: ${e.message}"))
            }
        }
    }

    // Restore a question and its answers by database id. Activity should call this to rehydrate
    // the SSoT from a persisted question when restoring state.
    fun restoreQuestionFromDatabase(questionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val q = databaseManager?.getQuestionById(questionId)
                val a = databaseManager?.getAnswersForQuestion(questionId) ?: emptyList()
                val ids = databaseManager?.getQuestionIds() ?: emptyList()
                val index = ids.indexOf(questionId).coerceAtLeast(0)
                _state.update { it.copy(questionIds = ids, currentQuestionIndex = index).copyWithQuestion(q, a) }
            } catch (e: Exception) {
                _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Error restoring question: ${e.message}"))
            }
        }
    }

    fun fetchFilteredQuestionIdsToState() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = state.value
            val subjectFilter = s.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
            val systemFilter = s.selectedSystemIds.takeIf { it.isNotEmpty() }?.toList()
            val pf = s.performanceFilter
            val ids = databaseManager?.getQuestionIds(subjectIds = subjectFilter, systemIds = systemFilter, performanceFilter = pf) ?: emptyList()
            _state.update { it.copy(questionIds = ids) }
        }
    }

    fun clearLogsFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                databaseManager?.clearLogs()
                _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Logs cleared"))
            } catch (e: Exception) {
                _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Failed to clear logs: ${e.message}"))
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
                    if (flushed > 0) _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Flushed $flushed logs"))
                } catch (e: Exception) {
                    _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Failed to flush logs: ${e.message}"))
                }
            } else {
                databaseManager?.clearPendingLogsBuffer()
            }
        }
    }

    fun resetAnswerState() {
        _state.update { it.copy(selectedAnswerId = null, answerSubmitted = false) }
    }

    fun setAnswerSubmissionState(submitted: Boolean, selectedAnswerId: Int?) {
        _state.update { it.copy(answerSubmitted = submitted, selectedAnswerId = selectedAnswerId) }
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
                _uiEvents.emit(com.medicalquiz.app.viewmodel.UiEvent.ShowToast("Unable to load performance for question $questionId"))
            }
        }
    }

    // Expose database manager for other helpers if needed (avoid direct DB calls from Activity)
    fun getDatabaseManager(): com.medicalquiz.app.data.database.DatabaseProvider? = databaseManager
}
