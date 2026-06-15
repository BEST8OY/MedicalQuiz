package com.medicalquiz.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicalquiz.app.shared.data.ActiveDatabaseHolder
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.data.SettingsRepository
import com.medicalquiz.app.shared.data.models.SubmissionMode
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.domain.ApplyFiltersUseCase
import com.medicalquiz.app.shared.domain.SnackbarSink
import com.medicalquiz.app.shared.orchestration.AppHistoryCoordinator
import com.medicalquiz.app.shared.ui.state.FilterUiState
import com.medicalquiz.app.shared.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scoped ViewModel for the Filter Hub screen.
 * Tracks selected filters, loads subjects/systems dynamically, and exposes session histories.
 */
class FilterViewModel(
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val historyCoordinator: AppHistoryCoordinator,
    private val applyFiltersUseCase: ApplyFiltersUseCase,
    private val sessionRepository: QuizSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val snackbarSink: SnackbarSink,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(FilterUiState.EMPTY)
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

    val historyEntries: StateFlow<List<QuizSessionRepository.QuizSession>> = sessionRepository.historyEntries

    private var lastFetchedSubjectIds: List<Long>? = null
    private var isInitializing = false

    init {
        viewModelScope.launch {
            activeDatabaseHolder.databaseName.collect { dbName ->
                if (dbName.isNotEmpty()) {
                    _state.update { it.copy(databaseName = dbName) }
                    initializeAfterDatabaseSwitch()
                } else {
                    _state.value = FilterUiState.EMPTY
                    lastFetchedSubjectIds = null
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.isLoggingEnabled.collect { enabled ->
                _state.update { it.copy(isLoggingEnabled = enabled) }
                saveSession()
            }
        }
        viewModelScope.launch {
            settingsRepository.submissionMode.collect { mode ->
                _state.update { it.copy(submissionMode = mode) }
                saveSession()
            }
        }
    }

    internal suspend fun initializeAfterDatabaseSwitch() {
        if (isInitializing) return
        isInitializing = true
        try {
        _state.update {
            it.copy(
                selectedSubjectIds = emptySet(),
                selectedSystemIds = emptySet(),
                performanceFilter = PerformanceFilter.ALL,
                previewQuestionCount = 0,
                isLoggingEnabled = settingsRepository.isLoggingEnabled.value,
                submissionMode = settingsRepository.submissionMode.value,
            )
        }
        lastFetchedSubjectIds = null

        // Pre-load from saved active session if matches current DB
        val savedSession = sessionRepository.restoreSessionAsync()
        if (savedSession != null && savedSession.databaseName == state.value.databaseName) {
            _state.update {
                it.copy(
                    selectedSubjectIds = savedSession.selectedSubjectIds.toSet(),
                    selectedSystemIds = savedSession.selectedSystemIds.toSet(),
                    performanceFilter = savedSession.performanceFilter,
                )
            }
        }

        fetchSubjects()
        fetchSystemsForSubjects(null)
        updatePreviewQuestionCountInternal()
        } finally {
            isInitializing = false
        }
    }

    fun fetchSubjects() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(subjectsResource = Resource.Loading) }
            val db = activeDatabaseHolder.databaseProvider.value
            try {
                val subjects = db?.getSubjects() ?: emptyList()
                _state.update { it.copy(subjectsResource = Resource.Success(subjects)) }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error"
                _state.update { it.copy(subjectsResource = Resource.Error(errorMessage)) }
                emitSnackbar("Error fetching subjects: $errorMessage")
            }
        }
    }

    fun fetchSystemsForSubjects(subjectIds: List<Long>?) {
        if (shouldSkipSystemFetch(subjectIds)) return
        
        lastFetchedSubjectIds = subjectIds?.toList() ?: emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(systemsResource = Resource.Loading) }
            val db = activeDatabaseHolder.databaseProvider.value
            try {
                val systems = db?.getSystems(subjectIds) ?: emptyList()
                _state.update { it.copy(systemsResource = Resource.Success(systems)) }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error"
                _state.update { it.copy(systemsResource = Resource.Error(errorMessage)) }
                emitSnackbar("Error fetching systems: $errorMessage")
            }
        }
    }

    private fun shouldSkipSystemFetch(subjectIds: List<Long>?): Boolean {
        val lastFetched = lastFetchedSubjectIds ?: return false
        val normalizedRequested = subjectIds?.toSet() ?: emptySet()
        return lastFetched.toSet() == normalizedRequested
    }

    fun applySelectedSubjects(newSubjectIds: Set<Long>, loadQuestions: Boolean = false) {
        viewModelScope.launch {
            val previouslySelectedSystems = state.value.selectedSystemIds
            _state.update { it.copy(selectedSubjectIds = newSubjectIds) }

            val db = activeDatabaseHolder.databaseProvider.value
            val prunedSelectedSystems = applyFiltersUseCase.pruneSystemsForSubjects(
                db = db,
                newSubjectIds = newSubjectIds,
                previouslySelectedSystems = previouslySelectedSystems,
            )
            _state.update { it.copy(selectedSystemIds = prunedSelectedSystems) }

            val subjectsForSystems = applyFiltersUseCase.subjectsForSystemsFetch(newSubjectIds)
            fetchSystemsForSubjects(subjectsForSystems)

            updatePreviewQuestionCountInternal()
            saveSession()
        }
    }

    fun applySelectedSystems(newSystemIds: Set<Long>, loadQuestions: Boolean = false) {
        viewModelScope.launch {
            val db = activeDatabaseHolder.databaseProvider.value
            val normalizedSelection = applyFiltersUseCase.normalizeSelectedSystems(
                db = db,
                selectedSubjectIds = state.value.selectedSubjectIds,
                newSystemIds = newSystemIds,
            )

            _state.update { it.copy(selectedSystemIds = normalizedSelection) }
            updatePreviewQuestionCountInternal()
            saveSession()
        }
    }

    fun setPerformanceFilter(filter: PerformanceFilter, loadQuestions: Boolean = false) {
        _state.update { it.copy(performanceFilter = filter) }
        viewModelScope.launch {
            updatePreviewQuestionCountInternal()
            saveSession()
        }
    }

    fun clearAllFilters() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedSubjectIds = emptySet(),
                    selectedSystemIds = emptySet(),
                    performanceFilter = PerformanceFilter.ALL
                )
            }
            updatePreviewQuestionCountInternal()
            saveSession()
        }
    }

    private suspend fun updatePreviewQuestionCountInternal() {
        val currentState = state.value
        val db = activeDatabaseHolder.databaseProvider.value
        val count = runCatching {
            applyFiltersUseCase.previewQuestionCount(
                db = db,
                selectedSubjectIds = currentState.selectedSubjectIds,
                selectedSystemIds = currentState.selectedSystemIds,
                performanceFilter = currentState.performanceFilter,
            )
        }.getOrDefault(0)
        _state.update { it.copy(previewQuestionCount = count) }
    }

    private suspend fun saveSession() {
        val currentState = state.value
        if (currentState.databaseName.isNotEmpty()) {
            sessionRepository.saveSessionAsync(
                databaseName = currentState.databaseName,
                selectedSubjectIds = currentState.selectedSubjectIds,
                selectedSystemIds = currentState.selectedSystemIds,
                performanceFilter = currentState.performanceFilter,
                currentQuestionIndex = 0,
                appendToHistory = false,
                isLoggingEnabled = currentState.isLoggingEnabled,
                submissionMode = currentState.submissionMode,
            )
        }
    }

    suspend fun getQuestionIdsForHistoryEntries(
        entries: List<QuizSessionRepository.QuizSession>
    ): String = withContext(Dispatchers.IO) {
        val db = activeDatabaseHolder.databaseProvider.value
        buildString {
            entries.forEach { entry ->
                val questionIds = db?.getQuestionIds(
                    subjectIds = entry.selectedSubjectIds,
                    systemIds = entry.selectedSystemIds,
                    performanceFilter = entry.performanceFilter
                ) ?: emptyList()
                questionIds.forEach { qid ->
                    appendLine(qid)
                }
            }
        }
    }

    fun deleteHistoryEntries(entryIds: Set<String>) {
        appScope.launch {
            historyCoordinator.deleteHistoryEntries(entryIds)
        }
    }

    fun renameHistoryEntry(entryId: String, newName: String) {
        appScope.launch {
            historyCoordinator.renameHistoryEntry(
                entryId = entryId,
                newName = newName,
            )
        }
    }

    fun restoreHistoryEntry(entry: QuizSessionRepository.QuizSession, onRestored: (String) -> Unit) {
        appScope.launch {
            val matchingDatabase = historyCoordinator.restoreHistoryEntry(
                entry = entry,
            )
            if (matchingDatabase != null) {
                onRestored(matchingDatabase)
            }
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        settingsRepository.setLoggingEnabled(enabled)
    }

    fun setSubmissionMode(mode: SubmissionMode) {
        settingsRepository.setSubmissionMode(mode)
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            snackbarSink.emitSnackbar(message)
        }
    }
}
