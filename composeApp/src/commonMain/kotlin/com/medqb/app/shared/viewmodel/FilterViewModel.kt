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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
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

        private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    }

    private val _state = MutableStateFlow(FilterUiState.EMPTY)
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

    private val _subjectsRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _systemsRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        val restoredDbName = savedStateHandle.get<String>(KEY_DATABASE_NAME).orEmpty()
        if (restoredDbName.isNotEmpty()) {
            _state.update { it.copy(databaseName = restoredDbName) }
        }

        setupDatabaseNameTracking()
        setupSubjectsFlow()
        setupSystemsFlow()
        setupPreviewCountFlow()
        setupSettingsCollectors()
        restoreSavedFilters()
    }

    private fun setupDatabaseNameTracking() {
        activeDatabaseHolder.databaseName
            .onEach { dbName ->
                if (dbName.isNotEmpty()) {
                    val dbChanged = dbName != _state.value.databaseName
                    _state.update { it.copy(databaseName = dbName) }
                    savedStateHandle[KEY_DATABASE_NAME] = dbName
                    if (dbChanged) {
                        savedStateHandle.remove<List<Long>>(KEY_SELECTED_SUBJECT_IDS)
                        savedStateHandle.remove<List<Long>>(KEY_SELECTED_SYSTEM_IDS)
                        savedStateHandle.remove<String>(KEY_PERFORMANCE_FILTER)
                        filterStateHolder.reset()
                    }
                } else {
                    val restoredName = savedStateHandle.get<String>(KEY_DATABASE_NAME).orEmpty()
                    _state.update { it.copy(databaseName = restoredName) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupSubjectsFlow() {
        combine(
            activeDatabaseHolder.databaseProvider,
            _subjectsRetry.map { activeDatabaseHolder.databaseProvider.value },
        ) { db, _ -> db }
            .distinctUntilChangedBy { it?.hashCode() }
            .flatMapLatest { db ->
                flow {
                    if (db == null) {
                        emit(Resource.Success(emptyList()))
                        return@flow
                    }
                    emit(Resource.Loading)
                    try {
                        val subjects = withContext(Dispatchers.IO) { db.getSubjects() }
                        emit(Resource.Success(subjects))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val msg = e.message ?: "Unknown error"
                        emit(Resource.Error(msg))
                        snackbarSink.emitSnackbar("Error fetching subjects: $msg")
                    }
                }
            }
            .onEach { resource -> _state.update { it.copy(subjectsResource = resource) } }
            .launchIn(viewModelScope)
    }

    private fun setupSystemsFlow() {
        combine(
            activeDatabaseHolder.databaseProvider,
            filterStateHolder.selectedSubjectIds,
            _systemsRetry.map { filterStateHolder.selectedSubjectIds.value },
        ) { db, subjectIds, _ -> db to subjectIds }
            .distinctUntilChangedBy { (db, ids) -> "${db?.hashCode()}:${ids}" }
            .flatMapLatest { (db, subjectIds) ->
                flow {
                    if (db == null || subjectIds.isEmpty()) {
                        emit(Resource.Success(emptyList()))
                        return@flow
                    }
                    emit(Resource.Loading)
                    try {
                        val systems = withContext(Dispatchers.IO) { db.getSystems(subjectIds.toList()) }
                        emit(Resource.Success(systems))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val msg = e.message ?: "Unknown error"
                        emit(Resource.Error(msg))
                        snackbarSink.emitSnackbar("Error fetching systems: $msg")
                    }
                }
            }
            .onEach { resource -> _state.update { it.copy(systemsResource = resource) } }
            .launchIn(viewModelScope)
    }

    private fun setupPreviewCountFlow() {
        combine(
            filterStateHolder.selectedSubjectIds,
            filterStateHolder.selectedSystemIds,
            filterStateHolder.performanceFilter,
            activeDatabaseHolder.databaseProvider,
        ) { subjects, systems, perf, db -> Quad(subjects, systems, perf, db) }
            .flatMapLatest { (subjects, systems, perf, db) ->
                flow {
                    val count = withContext(Dispatchers.IO) {
                        runCatching {
                            applyFiltersUseCase.previewQuestionCount(
                                db = db,
                                selectedSubjectIds = subjects,
                                selectedSystemIds = systems,
                                performanceFilter = perf,
                            )
                        }.getOrDefault(0)
                    }
                    emit(count)
                }
            }
            .onEach { count -> _state.update { it.copy(previewQuestionCount = count) } }
            .launchIn(viewModelScope)
    }

    private fun setupSettingsCollectors() {
        settingsRepository.isLoggingEnabled
            .onEach { enabled -> _state.update { it.copy(isLoggingEnabled = enabled) } }
            .launchIn(viewModelScope)
        settingsRepository.submissionMode
            .onEach { mode -> _state.update { it.copy(submissionMode = mode) } }
            .launchIn(viewModelScope)
    }

    private fun restoreSavedFilters() {
        savedStateHandle.get<List<Long>>(KEY_SELECTED_SUBJECT_IDS)?.toSet()?.let {
            filterStateHolder.updateSubjectIds(it)
        }
        savedStateHandle.get<List<Long>>(KEY_SELECTED_SYSTEM_IDS)?.toSet()?.let {
            filterStateHolder.updateSystemIds(it)
        }
        savedStateHandle.get<String>(KEY_PERFORMANCE_FILTER)?.let { name ->
            runCatching { PerformanceFilter.valueOf(name) }.getOrNull()?.let {
                filterStateHolder.updatePerformanceFilter(it)
            }
        }
    }

    fun fetchSubjects() {
        _subjectsRetry.tryEmit(Unit)
    }

    fun fetchSystemsForSubjects(subjectIds: List<Long>?) {
        if (subjectIds != null) {
            filterStateHolder.updateSubjectIds(subjectIds.toSet())
        }
        _systemsRetry.tryEmit(Unit)
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
}
