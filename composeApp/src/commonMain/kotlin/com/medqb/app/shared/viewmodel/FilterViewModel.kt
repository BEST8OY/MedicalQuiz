package com.medqb.app.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.domain.SnackbarSink
import com.medqb.app.shared.ui.state.FilterUiState
import com.medqb.app.shared.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FilterViewModel(
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val applyFiltersUseCase: ApplyFiltersUseCase,
    private val sessionRepository: QuizSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val snackbarSink: SnackbarSink,
) : ViewModel() {

    private val _state = MutableStateFlow(FilterUiState.EMPTY)
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

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
                    performanceFilter = PerformanceFilter.ALL,
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

    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            snackbarSink.emitSnackbar(message)
        }
    }
}
