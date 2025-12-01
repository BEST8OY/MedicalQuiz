package com.medicalquiz.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicalquiz.app.shared.data.CacheManager
import com.medicalquiz.app.shared.data.SettingsRepository
import com.medicalquiz.app.shared.data.database.DatabaseProvider
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.database.QuestionPerformance
import com.medicalquiz.app.shared.data.models.Subject
import com.medicalquiz.app.shared.data.models.System
import com.medicalquiz.app.shared.ui.QuizState
import com.medicalquiz.app.shared.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val MAX_SCROLL_CACHE_SIZE = 100

class QuizViewModel : ViewModel() {

    private var databaseManager: DatabaseProvider? = null
    internal var settingsRepository: SettingsRepository? = null
        private set
    private var cacheManager: CacheManager? = null
    private var settingsObservationJob: Job? = null

    private var testId = Random.nextLong().toString()

    private val _state = MutableStateFlow(QuizState.EMPTY)
    val state: StateFlow<QuizState> = _state.asStateFlow()

    val toolbarTitle = state.map { it.databaseName }.stateIn(
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

    fun setDatabaseManager(db: DatabaseProvider) {
        // 1. Reset state immediately so UI clears old data
        resetState()
        
        val oldDb = databaseManager
        databaseManager = db
        viewModelScope.launch(Dispatchers.IO) {
            try {
                oldDb?.closeDatabase()
            } catch (e: Exception) {
                println("Error closing old database: ${e.message}")
            }
            initializeAfterDatabaseSwitch()
        }
    }

    private fun resetState() {
        _state.update { currentState ->
            // Reset to empty state but preserve settings that shouldn't change
            QuizState.EMPTY.copy(
                isLoggingEnabled = currentState.isLoggingEnabled,
                showMetadata = currentState.showMetadata,
                databaseName = "" // Will be set shortly after
            )
        }
        scrollPositionCache.clear()
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
            println("Initializing after database switch")
            
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
            // No subjects selected initially, so no systems to fetch
            println("Database initialization completed")
        } catch (e: Exception) {
            println("Error during post-switch initialization: ${e.message}")
            emitToast("Database initialization incomplete: ${e.message}")
        }
    }

    fun getDatabaseManager(): DatabaseProvider? = databaseManager

    fun setTestId(id: String) {
        testId = id
    }

    fun getTestId(): String = testId

    fun loadQuestion(index: Int, resetAnswerState: Boolean = true) {
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
                if (question != null) {
                    loadPerformanceForQuestion(question.id)
                }
            } catch (e: Exception) {
                println("Error loading question $questionId: ${e.message}")
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

    fun setQuestionIds(ids: List<Long>) {
        _state.update { it.copy(questionIds = ids) }
    }

    fun loadFilteredQuestionIds() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentState = state.value
                val ids = fetchQuestionIdsWithFilters(
                    currentState.selectedSubjectIds.toList(),
                    currentState.selectedSystemIds.toList(),
                    currentState.performanceFilter
                )
                val currentQuestionId = currentState.questionIds.getOrNull(currentState.currentQuestionIndex)
                val newIndex = if (currentQuestionId != null && currentQuestionId in ids) {
                    ids.indexOf(currentQuestionId)
                } else {
                    0
                }
                _state.update { it.copy(questionIds = ids, currentQuestionIndex = newIndex) }
                loadQuestion(newIndex)
            } catch (e: Exception) {
                println("Error loading filtered questions: ${e.message}")
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
            _state.update { it.copy(selectedSubjectIds = newSubjectIds) }
            
            val validSystems = if (newSubjectIds.isEmpty()) {
                emptySet()
            } else {
                databaseManager?.getSystems(newSubjectIds.toList())
                    ?.map { it.id }
                    ?.toSet() ?: emptySet()
            }
            
            _state.update { it.copy(selectedSystemIds = validSystems) }
            updatePreviewQuestionCountInternal()
            if (loadQuestions) {
                loadFilteredQuestionIds()
            }
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
            _state.update { it.copy(selectedSystemIds = newSystemIds) }
            updatePreviewQuestionCountInternal()
            if (loadQuestions) {
                loadFilteredQuestionIds()
            }
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

    private suspend fun pruneInvalidSystems(): Set<Long> {
        val db = databaseManager ?: return emptySet()
        val subjects = state.value.selectedSubjectIds.toList()
        if (subjects.isEmpty()) return emptySet()
        return db.getSystems(subjects).map { it.id }.toSet()
    }

    fun setPerformanceFilter(filter: PerformanceFilter, loadQuestions: Boolean = true) {
        _state.update { it.copy(performanceFilter = filter) }
        viewModelScope.launch(Dispatchers.IO) {
            updatePreviewQuestionCountInternal()
            if (loadQuestions) {
                loadFilteredQuestionIds()
            }
        }
    }

    fun openPerformanceDialog() {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.OpenPerformanceDialog)
        }
    }

    fun setDatabaseName(name: String) {
        _state.update { it.copy(databaseName = name) }
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

    fun openMedia(urls: List<String>, startIndex: Int) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.OpenMedia(urls, startIndex))
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
        super.onCleared()
        settingsObservationJob?.cancel()
        // Use runBlocking as onCleared is synchronous and database needs to be closed
        kotlinx.coroutines.runBlocking {
            runCatching { databaseManager?.closeDatabase() }
        }
    }
}
