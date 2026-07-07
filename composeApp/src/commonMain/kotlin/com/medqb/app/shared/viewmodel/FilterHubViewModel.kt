package com.medqb.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.domain.SnackbarSink
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.orchestration.AppHistoryCoordinator
import com.medqb.app.shared.ui.screens.FilterPane
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class FilterHubViewModel(
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val applyFiltersUseCase: ApplyFiltersUseCase,
    private val settingsRepository: SettingsRepository,
    private val snackbarSink: SnackbarSink,
    private val filterStateHolder: FilterStateHolder,
    private val savedStateHandle: SavedStateHandle,
    private val historyCoordinator: AppHistoryCoordinator,
    private val sessionRepository: QuizSessionRepository,
) : ViewModel() {

    private companion object {
        const val KEY_DATABASE_NAME = "database_name"
        const val KEY_SELECTED_SUBJECT_IDS = "selected_subject_ids"
        const val KEY_SELECTED_SYSTEM_IDS = "selected_system_ids"
        const val KEY_PERFORMANCE_FILTER = "performance_filter"
        const val KEY_ACTIVE_PANE = "active_pane"

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

        val restoredPaneStr = savedStateHandle.get<String>(KEY_ACTIVE_PANE).orEmpty()
        if (restoredPaneStr.isNotEmpty()) {
            runCatching { FilterPane.valueOf(restoredPaneStr) }.getOrNull()?.let { pane ->
                _state.update { it.copy(activePane = pane) }
            }
        }

        setupDatabaseNameTracking()
        setupSubjectsFlow()
        setupSystemsFlow()
        setupPreviewCountFlow()
        setupSettingsCollectors()
        setupFilterSelectionSync()
        setupHistoryEntriesFlow()
        setupPendingFilterPaneSync()
        restoreSavedFilters()
    }

    fun setActivePane(pane: FilterPane) {
        _state.update { it.copy(activePane = pane) }
        savedStateHandle[KEY_ACTIVE_PANE] = pane.name
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
            _subjectsRetry.map { activeDatabaseHolder.databaseProvider.value }
                .onStart { emit(activeDatabaseHolder.databaseProvider.value) },
        ) { db, _ -> db }
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
                        snackbarSink.emitSnackbar(SnackbarMessage.Simple("Error fetching subjects: $msg"))
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
            _systemsRetry.map { filterStateHolder.selectedSubjectIds.value }
                .onStart { emit(filterStateHolder.selectedSubjectIds.value) },
        ) { db, subjectIds, _ -> db to subjectIds }
            .flatMapLatest { (db, subjectIds) ->
                flow {
                    if (db == null) {
                        emit(Resource.Success(emptyList()))
                        return@flow
                    }
                    emit(Resource.Loading)
                    try {
                        val systems = withContext(Dispatchers.IO) {
                            db.getSystems(subjectIds.takeIf { it.isNotEmpty() }?.toList())
                        }
                        emit(Resource.Success(systems))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val msg = e.message ?: "Unknown error"
                        emit(Resource.Error(msg))
                        snackbarSink.emitSnackbar(SnackbarMessage.Simple("Error fetching systems: $msg"))
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

    private fun setupFilterSelectionSync() {
        filterStateHolder.selectedSubjectIds
            .onEach { ids -> _state.update { it.copy(selectedSubjectIds = ids) } }
            .launchIn(viewModelScope)
        filterStateHolder.selectedSystemIds
            .onEach { ids -> _state.update { it.copy(selectedSystemIds = ids) } }
            .launchIn(viewModelScope)
        filterStateHolder.performanceFilter
            .onEach { filter -> _state.update { it.copy(performanceFilter = filter) } }
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

    private fun setupHistoryEntriesFlow() {
        combine(
            sessionRepository.historyEntries,
            activeDatabaseHolder.databaseName
        ) { entries, dbName ->
            val cleanDbName = dbName.removeSuffix(".db")
            entries.filter { it.databaseName == cleanDbName }
        }
        .onEach { filtered -> _state.update { it.copy(historyEntries = filtered) } }
        .launchIn(viewModelScope)
    }

    private fun setupPendingFilterPaneSync() {
        filterStateHolder.pendingFilterPane
            .filterNotNull()
            .onEach { pane ->
                setActivePane(pane)
                filterStateHolder.consumePendingFilterPane()
            }
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
            savedStateHandle[KEY_SELECTED_SUBJECT_IDS] = subjectIds
        }
        _systemsRetry.tryEmit(Unit)
    }

    fun applySelectedSubjects(newSubjectIds: Set<Long>) {
        viewModelScope.launch {
            val previouslySelectedSystems = filterStateHolder.selectedSystemIds.value
            filterStateHolder.updateSubjectIds(newSubjectIds)
            savedStateHandle[KEY_SELECTED_SUBJECT_IDS] = newSubjectIds.toList()
            val db = activeDatabaseHolder.databaseProvider.value
            val prunedSelectedSystems = applyFiltersUseCase.pruneSystemsForSubjects(
                db = db,
                newSubjectIds = newSubjectIds,
                previouslySelectedSystems = previouslySelectedSystems,
            )
            filterStateHolder.updateSystemIds(prunedSelectedSystems)
            savedStateHandle[KEY_SELECTED_SYSTEM_IDS] = prunedSelectedSystems.toList()
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
            savedStateHandle[KEY_SELECTED_SYSTEM_IDS] = normalizedSelection.toList()
        }
    }

    fun setPerformanceFilter(filter: PerformanceFilter) {
        filterStateHolder.updatePerformanceFilter(filter)
        savedStateHandle[KEY_PERFORMANCE_FILTER] = filter.name
    }

    fun clearAllFilters() {
        filterStateHolder.reset()
        savedStateHandle.remove<List<Long>>(KEY_SELECTED_SUBJECT_IDS)
        savedStateHandle.remove<List<Long>>(KEY_SELECTED_SYSTEM_IDS)
        savedStateHandle.remove<String>(KEY_PERFORMANCE_FILTER)
    }

    // History Pane logic
    suspend fun deleteHistoryEntries(entryIds: Set<String>) {
        historyCoordinator.deleteHistoryEntries(entryIds)
    }

    fun renameHistoryEntry(entryId: String, newName: String) {
        viewModelScope.launch {
            historyCoordinator.renameHistoryEntry(entryId = entryId, newName = newName)
        }
    }

    suspend fun undoHistoryEntry(entry: QuizSessionRepository.QuizSession) {
        sessionRepository.appendToHistory(
            databaseName = entry.databaseName,
            selectedSubjectIds = entry.selectedSubjectIds.toSet(),
            selectedSystemIds = entry.selectedSystemIds.toSet(),
            performanceFilter = entry.performanceFilter,
            currentQuestionIndex = entry.currentQuestionIndex,
            isLoggingEnabled = entry.isLoggingEnabled,
            submissionMode = entry.submissionMode,
            currentSessionId = entry.id,
            entryName = entry.entryName,
        )
    }

    fun restoreHistoryEntry(
        entry: QuizSessionRepository.QuizSession,
        onSuccess: suspend (String) -> Unit,
        onFailure: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val dbName = historyCoordinator.restoreHistoryEntry(entry)
                if (dbName != null) {
                    onSuccess(dbName)
                } else {
                    onFailure()
                }
            } catch (e: Exception) {
                onFailure()
            }
        }
    }

    fun copyQuestionIdsForHistoryEntries(
        entries: List<QuizSessionRepository.QuizSession>,
        onCopied: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val qids = historyCoordinator.getQuestionIdsForHistoryEntries(entries)
                onCopied(qids)
            } catch (e: Exception) {
                onCopied("")
            }
        }
    }
}
