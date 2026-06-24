package com.medqb.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.domain.SnackbarSink
import com.medqb.app.shared.ui.state.FilterUiState
import com.medqb.app.shared.utils.Resource
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Inject
class FilterViewModel(
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val applyFiltersUseCase: ApplyFiltersUseCase,
    private val settingsRepository: SettingsRepository,
    private val snackbarSink: SnackbarSink,
    private val filterStateHolder: FilterStateHolder,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private companion object {
        const val KEY_DATABASE_NAME = "database_name"
        const val KEY_SELECTED_SUBJECT_IDS = "selected_subject_ids"
        const val KEY_SELECTED_SYSTEM_IDS = "selected_system_ids"
        const val KEY_PERFORMANCE_FILTER = "performance_filter"
    }

    private val _state = MutableStateFlow(FilterUiState.EMPTY)
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

    private var lastFetchedSubjectIds: List<Long>? = null
    private var initJob: Job? = null
    private var subjectsJob: Job? = null
    private var systemsJob: Job? = null
    private var previewGeneration = 0L

    init {
        val restoredDbName = savedStateHandle.get<String>(KEY_DATABASE_NAME).orEmpty()
        if (restoredDbName.isNotEmpty()) {
            _state.update { it.copy(databaseName = restoredDbName) }
        }

        viewModelScope.launch {
            activeDatabaseHolder.databaseName.collect { dbName ->
                if (dbName.isNotEmpty()) {
                    val dbChanged = dbName != _state.value.databaseName
                    _state.update { it.copy(databaseName = dbName) }
                    savedStateHandle[KEY_DATABASE_NAME] = dbName
                    initializeAfterDatabaseSwitch(dbChanged = dbChanged)
                } else {
                    val restoredName = savedStateHandle.get<String>(KEY_DATABASE_NAME).orEmpty()
                    _state.update { it.copy(databaseName = restoredName) }
                    lastFetchedSubjectIds = null
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.isLoggingEnabled.collect { enabled ->
                _state.update { it.copy(isLoggingEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.submissionMode.collect { mode ->
                _state.update { it.copy(submissionMode = mode) }
            }
        }
        viewModelScope.launch {
            filterStateHolder.selectedSubjectIds.collect { subjectIds ->
                _state.update { it.copy(selectedSubjectIds = subjectIds) }
                savedStateHandle[KEY_SELECTED_SUBJECT_IDS] = subjectIds.toList()
                val subjectsForSystems = applyFiltersUseCase.subjectsForSystemsFetch(subjectIds)
                fetchSystemsForSubjects(subjectsForSystems)
                updatePreviewQuestionCountInternal()
            }
        }
        viewModelScope.launch {
            filterStateHolder.selectedSystemIds.collect { systemIds ->
                _state.update { it.copy(selectedSystemIds = systemIds) }
                savedStateHandle[KEY_SELECTED_SYSTEM_IDS] = systemIds.toList()
                updatePreviewQuestionCountInternal()
            }
        }
        viewModelScope.launch {
            filterStateHolder.performanceFilter.collect { filter ->
                _state.update { it.copy(performanceFilter = filter) }
                savedStateHandle[KEY_PERFORMANCE_FILTER] = filter.name
                updatePreviewQuestionCountInternal()
            }
        }
    }

    private fun initializeAfterDatabaseSwitch(dbChanged: Boolean) {
        initJob?.cancel()
        subjectsJob?.cancel()
        systemsJob?.cancel()
        previewGeneration++

        initJob = viewModelScope.launch {
            if (dbChanged) {
                savedStateHandle.remove<List<Long>>(KEY_SELECTED_SUBJECT_IDS)
                savedStateHandle.remove<List<Long>>(KEY_SELECTED_SYSTEM_IDS)
                savedStateHandle.remove<String>(KEY_PERFORMANCE_FILTER)
                _state.update { it.copy(previewQuestionCount = 0) }
                filterStateHolder.reset()
            } else {
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
            }

            fetchSubjects()
        }
    }

    fun fetchSubjects() {
        subjectsJob?.cancel()
        subjectsJob = viewModelScope.launch(Dispatchers.IO) {
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

        systemsJob?.cancel()
        systemsJob = viewModelScope.launch(Dispatchers.IO) {
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

    fun applySelectedSubjects(newSubjectIds: Set<Long>) {
        viewModelScope.launch {
            val previouslySelectedSystems = filterStateHolder.selectedSystemIds.value
            filterStateHolder.updateSubjectIds(newSubjectIds)

            val db = activeDatabaseHolder.databaseProvider.value
            val prunedSelectedSystems = applyFiltersUseCase.pruneSystemsForSubjects(
                db = db,
                newSubjectIds = newSubjectIds,
                previouslySelectedSystems = previouslySelectedSystems,
            )
            filterStateHolder.updateSystemIds(prunedSelectedSystems)
        }
    }

    fun applySelectedSystems(newSystemIds: Set<Long>) {
        viewModelScope.launch {
            val db = activeDatabaseHolder.databaseProvider.value
            val normalizedSelection = applyFiltersUseCase.normalizeSelectedSystems(
                db = db,
                selectedSubjectIds = filterStateHolder.selectedSubjectIds.value,
                newSystemIds = newSystemIds,
            )
            filterStateHolder.updateSystemIds(normalizedSelection)
        }
    }

    fun setPerformanceFilter(filter: PerformanceFilter) {
        filterStateHolder.updatePerformanceFilter(filter)
    }

    fun clearAllFilters() {
        filterStateHolder.reset()
    }

    private suspend fun updatePreviewQuestionCountInternal() {
        val currentState = state.value
        val gen = previewGeneration
        val db = activeDatabaseHolder.databaseProvider.value
        val count = withContext(Dispatchers.IO) {
            runCatching {
                applyFiltersUseCase.previewQuestionCount(
                    db = db,
                    selectedSubjectIds = currentState.selectedSubjectIds,
                    selectedSystemIds = currentState.selectedSystemIds,
                    performanceFilter = currentState.performanceFilter,
                )
            }.getOrDefault(0)
        }
        if (previewGeneration != gen) return
        _state.update { it.copy(previewQuestionCount = count) }
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            snackbarSink.emitSnackbar(message)
        }
    }
}
