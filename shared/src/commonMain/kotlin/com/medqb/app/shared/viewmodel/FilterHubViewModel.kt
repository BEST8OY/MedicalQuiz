package com.medqb.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.MarkedQuestionsRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.SettingsRepository
import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.models.QuestionFilter
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.domain.SnackbarSink
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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
    private val markedQuestionsRepository: MarkedQuestionsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private companion object {
        const val KEY_DATABASE_NAME = "database_name"
        const val KEY_SELECTED_SUBJECT_IDS = "selected_subject_ids"
        const val KEY_EXCLUDED_SUBJECT_IDS = "excluded_subject_ids"
        const val KEY_SELECTED_SYSTEM_IDS = "selected_system_ids"
        const val KEY_EXCLUDED_SYSTEM_IDS = "excluded_system_ids"
        const val KEY_STATUS_FILTER = "status_filter"
        const val KEY_SELECTED_DIFFICULTY_TIERS = "selected_difficulty_tiers"
        const val KEY_ACTIVE_PANE = "activePane"

        private data class PreviewFilterParameters(
            val filter: QuestionFilter,
            val db: DatabaseProvider?,
            val markedIdsTrigger: Set<Long> = emptySet(),
        )

        private data class DifficultyFilterParameters(
            val db: DatabaseProvider?,
            val filter: QuestionFilter,
            val markedIdsTrigger: Set<Long> = emptySet(),
        )
    }

    private val _state = MutableStateFlow(FilterUiState.EMPTY)
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

    private val _subjectsRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _systemsRetry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var lastKnownDbName: String? = null
    private var applySubjectsJob: Job? = null
    private var applySystemsJob: Job? = null
    private var restoreHistoryJob: Job? = null
    private val _isRestoringHistory = MutableStateFlow(false)
    val isRestoringHistory: StateFlow<Boolean> = _isRestoringHistory.asStateFlow()

    private val markedQuestionIdsState: StateFlow<Set<Long>> = activeDatabaseHolder.activeDatabase
        .map { it?.name.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { dbName ->
            if (dbName.isBlank()) flowOf(emptySet())
            else markedQuestionsRepository.observeMarkedQuestionIds(dbName)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        val restoredDbName = savedStateHandle.get<String>(KEY_DATABASE_NAME).orEmpty()
        if (restoredDbName.isNotEmpty()) {
            lastKnownDbName = restoredDbName
            _state.update { it.copy(databaseName = restoredDbName) }
        }

        val restoredPaneStr = savedStateHandle.get<String>(KEY_ACTIVE_PANE).orEmpty()
        if (restoredPaneStr.isNotEmpty()) {
            runCatching { FilterPane.valueOf(restoredPaneStr) }.getOrNull()?.let { pane ->
                _state.update { it.copy(activePane = pane) }
            }
        }

        restoreSavedFilters()
        setupDatabaseNameTracking()
        setupMarkedQuestionTracking()
        setupDifficultyTracking()
        setupSubjectsFlow()
        setupSystemsFlow()
        setupPreviewCountFlow()
        setupSettingsCollectors()
        setupFilterSelectionSync()
        setupHistoryEntriesFlow()
        setupFilterPersistence()
    }

    private fun setupMarkedQuestionTracking() {
        markedQuestionIdsState
            .onEach { ids ->
                _state.update { it.copy(markedQuestionCount = ids.size) }
            }
            .launchIn(viewModelScope)
    }

    fun setActivePane(pane: FilterPane) {
        _state.update { it.copy(activePane = pane) }
        savedStateHandle[KEY_ACTIVE_PANE] = pane.name
    }

    private fun setupDatabaseNameTracking() {
        activeDatabaseHolder.activeDatabase
            .map { it?.name.orEmpty() }
            .onEach { dbName ->
                if (dbName.isNotEmpty()) {
                    val previous = lastKnownDbName
                    val dbChanged = previous != null && previous != dbName
                    lastKnownDbName = dbName
                    _state.update { it.copy(databaseName = dbName) }
                    savedStateHandle[KEY_DATABASE_NAME] = dbName
                    if (dbChanged) {
                        filterStateHolder.reset()
                    }
                } else {
                    _state.update { it.copy(databaseName = "") }
                    savedStateHandle[KEY_DATABASE_NAME] = ""
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupDifficultyTracking() {
        combine(
            activeDatabaseHolder.activeDatabase,
            filterStateHolder.filter,
            markedQuestionIdsState,
        ) { active, filter, markedIds ->
            DifficultyFilterParameters(
                db = active?.provider,
                filter = filter.copy(selectedDifficultyTiers = emptySet()),
                markedIdsTrigger = if (QuestionStatus.MARKED in filter.statusFilter.statuses) markedIds else emptySet(),
            )
        }
            .distinctUntilChanged()
            .flatMapLatest { params ->
                flow {
                    val db = params.db
                    if (db == null) {
                        emit(false to emptyMap<DifficultyTier, Int>())
                    } else {
                        val (isAvail, counts) = withContext(ioDispatcher) {
                            try {
                                val isAvail = db.isDifficultyAvailable()
                                val counts = if (isAvail) {
                                    applyFiltersUseCase.getDifficultyCounts(
                                        db = db,
                                        filter = params.filter,
                                    )
                                } else {
                                    emptyMap()
                                }
                                isAvail to counts
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Logger.e("FilterHubViewModel", "Error fetching difficulty counts", e)
                                false to emptyMap()
                            }
                        }
                        emit(isAvail to counts)
                    }
                }
            }
            .onEach { (isAvail, counts) ->
                _state.update {
                    it.copy(isDifficultyAvailable = isAvail, difficultyCounts = counts)
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
            filterStateHolder.filter.map { it.selectedSubjectIds }.distinctUntilChanged(),
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
            filterStateHolder.filter,
            activeDatabaseHolder.activeDatabase,
            markedQuestionIdsState,
        ) { filter, active, markedIds ->
            PreviewFilterParameters(
                filter = filter,
                db = active?.provider,
                markedIdsTrigger = if (QuestionStatus.MARKED in filter.statusFilter.statuses) markedIds else emptySet(),
            )
        }
            .distinctUntilChanged()
            .flatMapLatest { params ->
                flow {
                    val count = withContext(ioDispatcher) {
                        try {
                            applyFiltersUseCase.previewQuestionCount(
                                db = params.db,
                                filter = params.filter,
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
     * instead of parallel mirrors, so partial-update interleavings are impossible.
     */
    private fun setupFilterSelectionSync() {
        filterStateHolder.filter
            .onEach { filter ->
                _state.update {
                    it.copy(
                        selectedSubjectIds = filter.selectedSubjectIds,
                        excludedSubjectIds = filter.excludedSubjectIds,
                        selectedSystemIds = filter.selectedSystemIds,
                        excludedSystemIds = filter.excludedSystemIds,
                        statusFilter = filter.statusFilter,
                        selectedDifficultyTiers = filter.selectedDifficultyTiers,
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
            .distinctUntilChanged()
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
        val subjectIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_SUBJECT_IDS)?.toSet() ?: emptySet()
        val excludedSubjectIds = savedStateHandle.get<List<Long>>(KEY_EXCLUDED_SUBJECT_IDS)?.toSet() ?: emptySet()
        val systemIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_SYSTEM_IDS)?.toSet() ?: emptySet()
        val excludedSystemIds = savedStateHandle.get<List<Long>>(KEY_EXCLUDED_SYSTEM_IDS)?.toSet() ?: emptySet()
        val statusFilter = savedStateHandle.get<String>(KEY_STATUS_FILTER)?.let { StatusFilter.deserialize(it) } ?: StatusFilter.ALL
        val difficultyTiers = savedStateHandle.get<List<String>>(KEY_SELECTED_DIFFICULTY_TIERS)?.mapNotNull { name ->
            runCatching { DifficultyTier.valueOf(name) }.getOrNull()
        }?.toSet() ?: emptySet()

        val restoredFilter = QuestionFilter.create(
            selectedSubjectIds = subjectIds,
            excludedSubjectIds = excludedSubjectIds,
            selectedSystemIds = systemIds,
            excludedSystemIds = excludedSystemIds,
            statusFilter = statusFilter,
            selectedDifficultyTiers = difficultyTiers,
        )
        if (!restoredFilter.isDefault) {
            filterStateHolder.updateFilter(restoredFilter)
        }
    }

    /**
     * Single-writer persistence: SavedStateHandle reactively mirrors the holder,
     * so mutations write only to the holder and can never diverge from what is
     * persisted. External resets (e.g. a database switch) propagate automatically.
     */
    private fun setupFilterPersistence() {
        filterStateHolder.filter
            .onEach { filter ->
                savedStateHandle[KEY_SELECTED_SUBJECT_IDS] = filter.selectedSubjectIds.toList()
                savedStateHandle[KEY_EXCLUDED_SUBJECT_IDS] = filter.excludedSubjectIds.toList()
                savedStateHandle[KEY_SELECTED_SYSTEM_IDS] = filter.selectedSystemIds.toList()
                savedStateHandle[KEY_EXCLUDED_SYSTEM_IDS] = filter.excludedSystemIds.toList()
                savedStateHandle[KEY_STATUS_FILTER] = filter.statusFilter.serialize()
                savedStateHandle[KEY_SELECTED_DIFFICULTY_TIERS] = filter.selectedDifficultyTiers.map { it.name }
            }
            .launchIn(viewModelScope)
    }

    fun fetchSubjects() {
        _subjectsRetry.tryEmit(Unit)
    }

    fun fetchSystems() {
        _systemsRetry.tryEmit(Unit)
    }

    fun applySelectedSubjects(newSubjectIds: Set<Long>, excludedSubjectIds: Set<Long> = emptySet()) {
        applySubjectsJob?.cancel()
        applySystemsJob?.cancel()
        applySubjectsJob = viewModelScope.launch {
            try {
                val db = activeDatabaseHolder.activeDatabase.value?.provider
                val currentFilter = filterStateHolder.filter.value
                val (prunedSelectedSystems, prunedExcludedSystems) = withContext(ioDispatcher) {
                    applyFiltersUseCase.pruneSystemsForSubjects(
                        db = db,
                        newSubjectIds = newSubjectIds,
                        selectedSystems = currentFilter.selectedSystemIds,
                        excludedSystems = currentFilter.excludedSystemIds,
                    )
                }
                filterStateHolder.setSubjectsAndSystems(
                    selectedSubjects = newSubjectIds,
                    excludedSubjects = excludedSubjectIds,
                    selectedSystems = prunedSelectedSystems,
                    excludedSystems = prunedExcludedSystems,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("FilterHubViewModel", "Error applying selected subjects", e)
            }
        }
    }

    fun applySelectedSystems(newSystemIds: Set<Long>, excludedSystemIds: Set<Long> = emptySet()) {
        applySystemsJob?.cancel()
        applySystemsJob = viewModelScope.launch {
            try {
                val db = activeDatabaseHolder.activeDatabase.value?.provider
                val selectedSubjectIds = filterStateHolder.filter.value.selectedSubjectIds
                val (normalizedSelection, normalizedExcluded) = withContext(ioDispatcher) {
                    applyFiltersUseCase.normalizeSelectedSystems(
                        db = db,
                        selectedSubjectIds = selectedSubjectIds,
                        selectedSystems = newSystemIds,
                        excludedSystems = excludedSystemIds,
                    )
                }
                filterStateHolder.setSystems(normalizedSelection, normalizedExcluded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("FilterHubViewModel", "Error applying selected systems", e)
            }
        }
    }

    fun setStatusFilter(filter: StatusFilter) {
        filterStateHolder.updateStatusFilter(filter)
    }

    fun setDifficultyFilters(tiers: Set<DifficultyTier>) {
        filterStateHolder.updateDifficultyTiers(tiers)
    }

    fun clearAllFilters() {
        filterStateHolder.reset()
    }

    fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLoggingEnabled(enabled)
        }
    }

    fun setSubmissionMode(mode: SubmissionMode) {
        viewModelScope.launch {
            settingsRepository.setSubmissionMode(mode)
        }
    }

    // History Pane logic
    fun deleteHistoryEntries(entryIds: Set<String>) {
        viewModelScope.launch {
            historyCoordinator.deleteHistoryEntries(entryIds)
        }
    }

    fun renameHistoryEntry(entryId: String, newName: String) {
        viewModelScope.launch {
            historyCoordinator.renameHistoryEntry(entryId = entryId, newName = newName)
        }
    }

    fun undoHistoryEntries(entries: List<QuizSessionRepository.QuizSession>) {
        viewModelScope.launch {
            entries.forEach { sessionRepository.restoreDeletedHistoryEntry(it) }
        }
    }

    fun restoreHistoryEntry(
        entry: QuizSessionRepository.QuizSession,
        onSuccess: suspend (String) -> Unit,
        onFailure: suspend () -> Unit
    ) {
        if (restoreHistoryJob?.isActive == true || _isRestoringHistory.value) return
        _isRestoringHistory.value = true
        restoreHistoryJob = viewModelScope.launch {
            try {
                val dbName = historyCoordinator.restoreHistoryEntry(entry)
                if (dbName != null) {
                    onSuccess(dbName)
                } else {
                    onFailure()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure()
            } finally {
                _isRestoringHistory.value = false
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
