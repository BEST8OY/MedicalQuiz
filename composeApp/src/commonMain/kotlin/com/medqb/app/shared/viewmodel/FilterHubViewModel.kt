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
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.ui.screens.filter.FilterPane
import com.medqb.app.shared.ui.state.FilterUiState
import com.medqb.app.shared.utils.Resource
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private companion object {
        const val KEY_DATABASE_NAME = "database_name"
        const val KEY_SELECTED_SUBJECT_IDS = "selected_subject_ids"
        const val KEY_SELECTED_SYSTEM_IDS = "selected_system_ids"
        const val KEY_PERFORMANCE_FILTER = "performance_filter"
        const val KEY_ACTIVE_PANE = "activePane"

        private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    }

    private val _state = MutableStateFlow(FilterUiState.EMPTY)
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

    private val _subjectsRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _systemsRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val activePaneState = savedStateHandle.getMutableStateFlow<String?>(KEY_ACTIVE_PANE, null)
    private val databaseNameState = savedStateHandle.getMutableStateFlow(KEY_DATABASE_NAME, "")

    init {
        val restoredDbName = databaseNameState.value
        if (restoredDbName.isNotEmpty()) {
            _state.update { it.copy(databaseName = restoredDbName) }
        }

        val restoredPaneStr = activePaneState.value.orEmpty()
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
        setupInitialPaneCollector()
        setupFilterPersistence()
        restoreSavedFilters()
    }

    fun setActivePane(pane: FilterPane) {
        _state.update { it.copy(activePane = pane) }
        activePaneState.value = pane.name
    }

    private fun setupInitialPaneCollector() {
        activePaneState
            .filterNotNull()
            .onEach { paneName ->
                runCatching { FilterPane.valueOf(paneName) }.getOrNull()?.let { pane ->
                    if (_state.value.activePane != pane) {
                        _state.update { it.copy(activePane = pane) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupDatabaseNameTracking() {
        activeDatabaseHolder.activeDatabase
            .map { it?.name.orEmpty() }
            .onEach { dbName ->
                if (dbName.isNotEmpty()) {
                    val dbChanged = dbName != _state.value.databaseName
                    _state.update { it.copy(databaseName = dbName) }
                    databaseNameState.value = dbName
                    if (dbChanged) {
                        // Holder reset propagates to SavedStateHandle through the
                        // filter-persistence collector.
                        filterStateHolder.reset()
                    }
                } else {
                    val restoredName = databaseNameState.value
                    _state.update { it.copy(databaseName = restoredName) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupSubjectsFlow() {
        combine(
            activeDatabaseHolder.activeDatabase,
            _subjectsRetry.onStart { emit(Unit) },
        ) { active, _ -> active?.provider }
            .flatMapLatest { db ->
                flow {
                    if (db == null) {
                        emit(Resource.Success(emptyList()))
                        return@flow
                    }
                    emit(Resource.Loading)
                    try {
                        val subjects = withContext(ioDispatcher) { db.getSubjects() }
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
            activeDatabaseHolder.activeDatabase,
            filterStateHolder.selectedSubjectIds,
            _systemsRetry.onStart { emit(Unit) },
        ) { active, subjectIds, _ -> (active?.provider) to subjectIds }
            .flatMapLatest { (db, subjectIds) ->
                flow {
                    if (db == null) {
                        emit(Resource.Success(emptyList()))
                        return@flow
                    }
                    emit(Resource.Loading)
                    try {
                        val systems = withContext(ioDispatcher) {
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
            activeDatabaseHolder.activeDatabase,
        ) { subjects, systems, perf, active -> Quad(subjects, systems, perf, active?.provider) }
            .flatMapLatest { (subjects, systems, perf, db) ->
                flow {
                    val count = withContext(ioDispatcher) {
                        try {
                            applyFiltersUseCase.previewQuestionCount(
                                db = db,
                                selectedSubjectIds = subjects,
                                selectedSystemIds = systems,
                                performanceFilter = perf,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Logger.e("FilterHubViewModel", "Error computing preview question count", e)
                            0
                        }
                    }
                    emit(count)
                }
            }
            .onEach { count -> _state.update { it.copy(previewQuestionCount = count) } }
            .launchIn(viewModelScope)
    }

    /**
     * Single reduction of shared filter-holder state into UiState — one collector
     * instead of three parallel mirrors, so partial-update interleavings are impossible.
     */
    private fun setupFilterSelectionSync() {
        combine(
            filterStateHolder.selectedSubjectIds,
            filterStateHolder.selectedSystemIds,
            filterStateHolder.performanceFilter,
        ) { subjectIds, systemIds, performanceFilter ->
            Triple(subjectIds, systemIds, performanceFilter)
        }
            .onEach { (subjectIds, systemIds, performanceFilter) ->
                _state.update {
                    it.copy(
                        selectedSubjectIds = subjectIds,
                        selectedSystemIds = systemIds,
                        performanceFilter = performanceFilter
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupSettingsCollectors() {
        combine(
            settingsRepository.isLoggingEnabled,
            settingsRepository.submissionMode,
        ) { isLoggingEnabled, submissionMode ->
            isLoggingEnabled to submissionMode
        }
            .onEach { (isLoggingEnabled, submissionMode) ->
                _state.update {
                    it.copy(isLoggingEnabled = isLoggingEnabled, submissionMode = submissionMode)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupHistoryEntriesFlow() {
        combine(
            sessionRepository.historyEntries,
            activeDatabaseHolder.activeDatabase,
        ) { entries, active ->
            val cleanDbName = active?.name.orEmpty()
            entries.filter { it.databaseName == cleanDbName }
        }
        .distinctUntilChanged()
        .onEach { filtered -> _state.update { it.copy(historyEntries = filtered) } }
        .launchIn(viewModelScope)
    }

    /**
     * Hydrates the holder from SavedStateHandle once at creation. Persistence in
     * the other direction is continuous — see [setupFilterPersistence].
     */
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

    /**
     * Single-writer persistence: SavedStateHandle reactively mirrors the holder,
     * so mutations write only to the holder and can never diverge from what is
     * persisted. External resets (e.g. a database switch) propagate automatically.
     */
    private fun setupFilterPersistence() {
        combine(
            filterStateHolder.selectedSubjectIds,
            filterStateHolder.selectedSystemIds,
            filterStateHolder.performanceFilter,
        ) { subjectIds, systemIds, performanceFilter ->
            Triple(subjectIds, systemIds, performanceFilter)
        }
            .onEach { (subjectIds, systemIds, performanceFilter) ->
                savedStateHandle[KEY_SELECTED_SUBJECT_IDS] = subjectIds.toList()
                savedStateHandle[KEY_SELECTED_SYSTEM_IDS] = systemIds.toList()
                savedStateHandle[KEY_PERFORMANCE_FILTER] = performanceFilter.name
            }
            .launchIn(viewModelScope)
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
            val db = activeDatabaseHolder.activeDatabase.value?.provider
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
            val db = activeDatabaseHolder.activeDatabase.value?.provider
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
        sessionRepository.restoreDeletedHistoryEntry(entry)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("FilterHubViewModel", "Error copying question ids for history entries", e)
                onCopied("")
            }
        }
    }
}
