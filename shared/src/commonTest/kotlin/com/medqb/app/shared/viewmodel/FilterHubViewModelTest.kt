package com.medqb.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope
import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.orchestration.AppHistoryCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.System
import com.medqb.app.shared.utils.Resource
import com.medqb.app.shared.ui.dialogs.ItemFilterState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FilterHubViewModelTest {

    private val scheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runHubTest(testBody: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher(scheduler)) {
        testBody()
    }

    private fun createViewModel(
        provider: FakeDatabaseProvider,
        holder: ActiveDatabaseHolder,
        filterStateHolder: FilterStateHolder = FilterStateHolder(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        markedQuestionsRepository: FakeMarkedQuestionsRepository = FakeMarkedQuestionsRepository(),
    ): FilterHubViewModel {
        val sessionRepository = FakeQuizSessionRepository()
        return FilterHubViewModel(
            activeDatabaseHolder = holder,
            applyFiltersUseCase = ApplyFiltersUseCase(),
            settingsRepository = FakeSettingsRepository(isLoggingEnabled = true),
            snackbarSink = FakeSnackbarSink(),
            filterStateHolder = filterStateHolder,
            savedStateHandle = savedStateHandle,
            historyCoordinator = AppHistoryCoordinator(
                sessionRepository = sessionRepository,
                localContentRepository = LocalContentRepository(),
                activeDatabaseHolder = holder,
            ),
            sessionRepository = sessionRepository,
            markedQuestionsRepository = markedQuestionsRepository,
            ioDispatcher = StandardTestDispatcher(scheduler),
        )
    }

    @Test
    fun previewCountTracksStatusFilterAndSelectionMirrorsHolder() = runHubTest {
        val provider = FakeDatabaseProvider() // count: 10 for ALL, 3 otherwise
        val filterStateHolder = FilterStateHolder()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder = filterStateHolder)
        provider.installInto(holder)
        advanceUntilIdle()

        assertEquals(10, viewModel.state.value.previewQuestionCount)

        filterStateHolder.updateSubjectIds(setOf(5L))
        advanceUntilIdle()
        assertEquals(setOf(5L), viewModel.state.value.selectedSubjectIds)

        val lastCorrect = StatusFilter(setOf(QuestionStatus.ANSWERED_CORRECTLY), StatusScope.LATEST_ATTEMPT)
        filterStateHolder.updateStatusFilter(lastCorrect)
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.previewQuestionCount)
        assertEquals(lastCorrect, viewModel.state.value.statusFilter)
    }

    @Test
    fun clearAllFiltersResetsHolderAndMirroredState() = runHubTest {
        val provider = FakeDatabaseProvider()
        val filterStateHolder = FilterStateHolder()
        val savedStateHandle = SavedStateHandle()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder, savedStateHandle)
        provider.installInto(holder)
        advanceUntilIdle()

        val everCorrect = StatusFilter(setOf(QuestionStatus.ANSWERED_CORRECTLY), StatusScope.ALL_ATTEMPTS)
        viewModel.setStatusFilter(everCorrect)
        filterStateHolder.updateSubjectIds(setOf(1L, 2L))
        advanceUntilIdle()
        assertEquals(everCorrect, viewModel.state.value.statusFilter)
        // SavedStateHandle mirrors the holder reactively.
        assertEquals(listOf(1L, 2L), savedStateHandle.get<List<Long>>("selected_subject_ids"))
        assertEquals(everCorrect.serialize(), savedStateHandle.get<String>("status_filter"))

        viewModel.clearAllFilters()
        advanceUntilIdle()

        assertEquals(emptySet(), filterStateHolder.selectedSubjectIds.value)
        assertEquals(emptySet(), filterStateHolder.selectedSystemIds.value)
        assertEquals(StatusFilter.ALL, filterStateHolder.statusFilter.value)
        assertEquals(emptySet(), viewModel.state.value.selectedSubjectIds)
        assertEquals(StatusFilter.ALL, viewModel.state.value.statusFilter)
        assertEquals(emptyList(), savedStateHandle.get<List<Long>>("selected_subject_ids"))
        assertEquals(StatusFilter.ALL.serialize(), savedStateHandle.get<String>("status_filter"))
    }

    @Test
    fun externalHolderResetPropagatesToSavedState() = runHubTest {
        val provider = FakeDatabaseProvider()
        val filterStateHolder = FilterStateHolder()
        val savedStateHandle = SavedStateHandle()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder, savedStateHandle)
        provider.installInto(holder)
        advanceUntilIdle()

        val everIncorrect = StatusFilter(setOf(QuestionStatus.ANSWERED_INCORRECTLY), StatusScope.ALL_ATTEMPTS)
        viewModel.setStatusFilter(everIncorrect)
        filterStateHolder.updateSubjectIds(setOf(9L))
        advanceUntilIdle()
        assertEquals(everIncorrect.serialize(), savedStateHandle.get<String>("status_filter"))

        // Simulates an out-of-ViewModel reset (e.g. AppWorkflowCoordinator on db select):
        // with reactive persistence it must still reach SavedStateHandle.
        filterStateHolder.reset()
        advanceUntilIdle()

        assertEquals(emptyList(), savedStateHandle.get<List<Long>>("selected_subject_ids"))
        assertEquals(StatusFilter.ALL.serialize(), savedStateHandle.get<String>("status_filter"))
    }

    @Test
    fun databaseSwitchResetsSavedFilters() = runHubTest {
        val firstProvider = FakeDatabaseProvider(dbName = "bank-a")
        val filterStateHolder = FilterStateHolder()
        val savedStateHandle = SavedStateHandle()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(firstProvider, holder, filterStateHolder, savedStateHandle)
        firstProvider.installInto(holder)
        advanceUntilIdle()

        filterStateHolder.updateSubjectIds(setOf(7L))
        advanceUntilIdle()
        assertEquals(setOf(7L), viewModel.state.value.selectedSubjectIds)

        // Switching databases clears prior selections.
        val secondProvider = FakeDatabaseProvider(dbName = "bank-b")
        secondProvider.installInto(holder)
        advanceUntilIdle()

        assertEquals("bank-b", viewModel.state.value.databaseName)
        assertEquals(emptySet(), viewModel.state.value.selectedSubjectIds)
        assertEquals(emptySet(), filterStateHolder.selectedSubjectIds.value)
    }

    @Test
    fun closingDatabaseClearsDatabaseName() = runHubTest {
        val provider = FakeDatabaseProvider(dbName = "bank-a")
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder)
        provider.installInto(holder)
        advanceUntilIdle()

        assertEquals("bank-a", viewModel.state.value.databaseName)

        holder.closeDatabase()
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.databaseName)
    }

    @Test
    fun fetchSystemsRetriesWithoutMutatingSelectedSubjects() = runHubTest {
        val provider = FakeDatabaseProvider(dbName = "bank-nbme")
        provider.seededSystems = listOf(System(id = 0, name = "All Systems", count = 50))
        val filterStateHolder = FilterStateHolder()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder = filterStateHolder)
        provider.installInto(holder)
        advanceUntilIdle()

        // User selected subject 1
        filterStateHolder.updateSubjectIds(setOf(1L))
        advanceUntilIdle()
        assertEquals(setOf(1L), filterStateHolder.selectedSubjectIds.value)
        assertEquals(setOf(1L), viewModel.state.value.selectedSubjectIds)

        // Calling fetchSystems() should retry without mutating selectedSubjectIds
        viewModel.fetchSystems()
        assertEquals(setOf(1L), filterStateHolder.selectedSubjectIds.value)
        advanceUntilIdle()
        assertEquals(setOf(1L), viewModel.state.value.selectedSubjectIds)

        // Calling fetchSubjects() should retry without mutating selectedSubjectIds
        viewModel.fetchSubjects()
        assertEquals(setOf(1L), filterStateHolder.selectedSubjectIds.value)
        advanceUntilIdle()
        assertEquals(setOf(1L), viewModel.state.value.selectedSubjectIds)
    }

    @Test
    fun systemsResourcePreservesPreloadedSuccessWithoutSpuriousReset() = runHubTest {
        val provider = FakeDatabaseProvider(dbName = "bank-nbme")
        val nbmeSystem = System(id = 0, name = "All Systems", count = 50)
        provider.seededSystems = listOf(nbmeSystem)
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder)
        provider.installInto(holder)
        advanceUntilIdle()

        // Before user taps anything, systemsResource is already preloaded as Resource.Success
        val initialResource = viewModel.state.value.systemsResource
        assertTrue(initialResource is Resource.Success)
        assertEquals(listOf(nbmeSystem), initialResource.data)

        // Simply opening the dialog does NOT call fetchSystems(), so state remains Success
        assertEquals(listOf(nbmeSystem), (viewModel.state.value.systemsResource as Resource.Success).data)
    }

    @Test
    fun difficultyFilterSelectionMirrorsHolderAndPersists() = runHubTest {
        val provider = FakeDatabaseProvider()
        val filterStateHolder = FilterStateHolder()
        val savedStateHandle = SavedStateHandle()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder, savedStateHandle)
        provider.installInto(holder)
        advanceUntilIdle()

        viewModel.setDifficultyFilters(setOf(DifficultyTier.EASY, DifficultyTier.INTERMEDIATE))
        advanceUntilIdle()

        assertEquals(
            setOf(DifficultyTier.EASY, DifficultyTier.INTERMEDIATE),
            viewModel.state.value.selectedDifficultyTiers
        )
        val persisted = savedStateHandle.get<List<String>>("selected_difficulty_tiers")
        assertEquals(setOf("EASY", "INTERMEDIATE"), persisted?.toSet())

        // Reset clears difficulty
        viewModel.clearAllFilters()
        advanceUntilIdle()
        assertEquals(emptySet(), viewModel.state.value.selectedDifficultyTiers)
        assertEquals(emptyList(), savedStateHandle.get<List<String>>("selected_difficulty_tiers"))
    }

    @Test
    fun difficultyCountsUpdateWhenSubjectsOrSystemsChange() = runHubTest {
        val activeStatusFilter = StatusFilter(setOf(QuestionStatus.ANSWERED_CORRECTLY), StatusScope.LATEST_ATTEMPT)
        val provider = FakeDatabaseProvider()
        provider.difficultyAvailable = true
        provider.difficultyCountsProvider = { subjects, _, systems, _, perf ->
            when {
                subjects == listOf(10L) -> mapOf(
                    DifficultyTier.VERY_DIFFICULT to 0,
                    DifficultyTier.EASY to 4,
                )
                systems == listOf(20L) -> mapOf(
                    DifficultyTier.VERY_DIFFICULT to 1,
                    DifficultyTier.EASY to 2,
                )
                perf == activeStatusFilter -> mapOf(
                    DifficultyTier.VERY_DIFFICULT to 3,
                    DifficultyTier.EASY to 5,
                )
                else -> mapOf(
                    DifficultyTier.VERY_DIFFICULT to 10,
                    DifficultyTier.EASY to 20,
                )
            }
        }

        val filterStateHolder = FilterStateHolder()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder = filterStateHolder)
        provider.installInto(holder)
        advanceUntilIdle()

        // Global bank counts initially
        assertTrue(viewModel.state.value.isDifficultyAvailable)
        assertEquals(10, viewModel.state.value.difficultyCounts[DifficultyTier.VERY_DIFFICULT])
        assertEquals(20, viewModel.state.value.difficultyCounts[DifficultyTier.EASY])

        // Scoped by subject
        filterStateHolder.updateSubjectIds(setOf(10L))
        advanceUntilIdle()
        assertEquals(0, viewModel.state.value.difficultyCounts[DifficultyTier.VERY_DIFFICULT])
        assertEquals(4, viewModel.state.value.difficultyCounts[DifficultyTier.EASY])

        // Scoped by system
        filterStateHolder.updateSubjectIds(emptySet())
        filterStateHolder.updateSystemIds(setOf(20L))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.difficultyCounts[DifficultyTier.VERY_DIFFICULT])
        assertEquals(2, viewModel.state.value.difficultyCounts[DifficultyTier.EASY])

        // Scoped by status filter
        filterStateHolder.updateSystemIds(emptySet())
        filterStateHolder.updateStatusFilter(activeStatusFilter)
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.difficultyCounts[DifficultyTier.VERY_DIFFICULT])
        assertEquals(5, viewModel.state.value.difficultyCounts[DifficultyTier.EASY])
    }

    @Test
    fun itemFilterStateCyclesCorrectly() {
        assertEquals(ItemFilterState.INCLUDED, ItemFilterState.NEUTRAL.next())
        assertEquals(ItemFilterState.EXCLUDED, ItemFilterState.INCLUDED.next())
        assertEquals(ItemFilterState.NEUTRAL, ItemFilterState.EXCLUDED.next())
    }

    @Test
    fun exclusionStateTracksHolderAndPersistsInSavedState() = runHubTest {
        val provider = FakeDatabaseProvider()
        provider.seededSubjects = listOf(
            Subject(id = 14L, name = "Emergency Medicine", count = 554),
            Subject(id = 21L, name = "Gynecology", count = 320),
        )
        provider.seededSystems = listOf(
            System(id = 100L, name = "Cardiovascular", count = 200),
            System(id = 200L, name = "Pulmonary", count = 150),
        )
        val filterStateHolder = FilterStateHolder()
        val savedStateHandle = SavedStateHandle()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder = filterStateHolder, savedStateHandle = savedStateHandle)
        provider.installInto(holder)
        advanceUntilIdle()

        // Apply subjects with both include (Emergency Medicine = 14) and exclude (Gynecology = 21)
        viewModel.applySelectedSubjects(newSubjectIds = setOf(14L), excludedSubjectIds = setOf(21L))
        advanceUntilIdle()

        assertEquals(setOf(14L), viewModel.state.value.selectedSubjectIds)
        assertEquals(setOf(21L), viewModel.state.value.excludedSubjectIds)
        assertEquals(listOf(14L), savedStateHandle.get<List<Long>>("selected_subject_ids"))
        assertEquals(listOf(21L), savedStateHandle.get<List<Long>>("excluded_subject_ids"))

        // Apply systems with include (Cardio = 100) and exclude (Pulm = 200)
        viewModel.applySelectedSystems(newSystemIds = setOf(100L), excludedSystemIds = setOf(200L))
        advanceUntilIdle()

        assertEquals(setOf(100L), viewModel.state.value.selectedSystemIds)
        assertEquals(setOf(200L), viewModel.state.value.excludedSystemIds)
        assertEquals(listOf(100L), savedStateHandle.get<List<Long>>("selected_system_ids"))
        assertEquals(listOf(200L), savedStateHandle.get<List<Long>>("excluded_system_ids"))

        // Clear all filters resets both included and excluded
        viewModel.clearAllFilters()
        advanceUntilIdle()

        assertEquals(emptySet(), viewModel.state.value.selectedSubjectIds)
        assertEquals(emptySet(), viewModel.state.value.excludedSubjectIds)
        assertEquals(emptySet(), viewModel.state.value.selectedSystemIds)
        assertEquals(emptySet(), viewModel.state.value.excludedSystemIds)
        assertEquals(emptyList(), savedStateHandle.get<List<Long>>("selected_subject_ids"))
        assertEquals(emptyList(), savedStateHandle.get<List<Long>>("excluded_subject_ids"))
        assertEquals(emptyList(), savedStateHandle.get<List<Long>>("selected_system_ids"))
        assertEquals(emptyList(), savedStateHandle.get<List<Long>>("excluded_system_ids"))
    }

    @Test
    fun exclusionAffectsPreviewCountAndDifficultyCounts() = runHubTest {
        val provider = FakeDatabaseProvider()
        provider.difficultyAvailable = true

        // Simulate 554 total for Emergency Medicine (14), 520 when Gynecology (21) is excluded
        provider.countQuestionIdsProvider = { subjects, exclSubjects, _, _, _, _ ->
            when {
                subjects == listOf(14L) && exclSubjects == listOf(21L) -> 520
                subjects == listOf(14L) -> 554
                exclSubjects == listOf(21L) -> 3181
                else -> 3501
            }
        }
        provider.difficultyCountsProvider = { subjects, exclSubjects, _, _, _ ->
            when {
                subjects == listOf(14L) && exclSubjects == listOf(21L) -> mapOf(
                    DifficultyTier.VERY_DIFFICULT to 25,
                    DifficultyTier.EASY to 100,
                )
                subjects == listOf(14L) -> mapOf(
                    DifficultyTier.VERY_DIFFICULT to 30,
                    DifficultyTier.EASY to 110,
                )
                else -> mapOf(
                    DifficultyTier.VERY_DIFFICULT to 150,
                    DifficultyTier.EASY to 500,
                )
            }
        }

        val filterStateHolder = FilterStateHolder()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder = filterStateHolder)
        provider.installInto(holder)
        advanceUntilIdle()

        assertEquals(3501, viewModel.state.value.previewQuestionCount)
        assertEquals(150, viewModel.state.value.difficultyCounts[DifficultyTier.VERY_DIFFICULT])

        // Include Emergency Medicine only
        viewModel.applySelectedSubjects(setOf(14L), emptySet())
        advanceUntilIdle()
        assertEquals(554, viewModel.state.value.previewQuestionCount)
        assertEquals(30, viewModel.state.value.difficultyCounts[DifficultyTier.VERY_DIFFICULT])

        // Include Emergency Medicine AND Exclude Gynecology
        viewModel.applySelectedSubjects(setOf(14L), setOf(21L))
        advanceUntilIdle()
        assertEquals(520, viewModel.state.value.previewQuestionCount)
        assertEquals(25, viewModel.state.value.difficultyCounts[DifficultyTier.VERY_DIFFICULT])
    }

    @Test
    fun excludingSubjectWithoutInclusionPreservesExistingSelectedAndExcludedSystems() = runHubTest {
        val provider = FakeDatabaseProvider()
        provider.seededSystems = listOf(
            System(id = 100L, name = "Cardiovascular", count = 200),
            System(id = 200L, name = "Pulmonary", count = 150),
        )
        val filterStateHolder = FilterStateHolder()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder = filterStateHolder)
        provider.installInto(holder)
        advanceUntilIdle()

        // User first selects Cardio (100) and excludes Pulm (200)
        viewModel.applySelectedSystems(newSystemIds = setOf(100L), excludedSystemIds = setOf(200L))
        advanceUntilIdle()
        assertEquals(setOf(100L), viewModel.state.value.selectedSystemIds)
        assertEquals(setOf(200L), viewModel.state.value.excludedSystemIds)

        // User then opens subjects dialog and ONLY excludes Gynecology (21), no included subjects
        viewModel.applySelectedSubjects(newSubjectIds = emptySet(), excludedSubjectIds = setOf(21L))
        advanceUntilIdle()

        // Systems must remain preserved!
        assertEquals(setOf(100L), viewModel.state.value.selectedSystemIds)
        assertEquals(setOf(200L), viewModel.state.value.excludedSystemIds)
        assertEquals(emptySet(), viewModel.state.value.selectedSubjectIds)
        assertEquals(setOf(21L), viewModel.state.value.excludedSubjectIds)
    }

    @Test
    fun markedQuestionCountUpdatesReactivelyWhenRepositoryEmits() = runHubTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val markedRepo = FakeMarkedQuestionsRepository()
        val viewModel = createViewModel(provider, holder, markedQuestionsRepository = markedRepo)
        provider.installInto(holder)
        advanceUntilIdle()

        // Initially 0 marked
        assertEquals(0, viewModel.state.value.markedQuestionCount)

        // Seed 3 marked questions
        markedRepo.seedMarked("bank", 1L, 2L, 3L)
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.markedQuestionCount)

        // Clear marked for database
        markedRepo.clearForDatabase("bank")
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.markedQuestionCount)
    }

    @Test
    fun markedFilterRecomputesPreviewCountWhenMarkedChanges() = runHubTest {
        val provider = FakeDatabaseProvider()
        val holder = ActiveDatabaseHolder()
        val markedRepo = FakeMarkedQuestionsRepository()
        val filterStateHolder = FilterStateHolder()
        var countQueries = 0
        provider.countQuestionIdsProvider = { _, _, _, _, filter, _ ->
            countQueries++
            if (QuestionStatus.MARKED in filter.statuses) {
                markedRepo.markedIdsFor("bank").size
            } else {
                10
            }
        }

        val viewModel = createViewModel(
            provider = provider,
            holder = holder,
            filterStateHolder = filterStateHolder,
            markedQuestionsRepository = markedRepo,
        )
        provider.installInto(holder)
        advanceUntilIdle()

        // With default filter (isAll = true), preview count is 10
        assertEquals(10, viewModel.state.value.previewQuestionCount)
        val queriesBefore = countQueries

        // Seed marks while MARKED is not selected: no redundant query re-computation
        markedRepo.seedMarked("bank", 1L, 2L)
        advanceUntilIdle()
        assertEquals(queriesBefore, countQueries)

        // Select MARKED status filter
        filterStateHolder.updateStatusFilter(StatusFilter(setOf(QuestionStatus.MARKED)))
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.previewQuestionCount)

        // Now seed another mark — preview count recomputes reactively
        markedRepo.seedMarked("bank", 3L)
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.previewQuestionCount)
    }

    @Test
    fun restoresFiltersFromSavedStateHandleOnCreation() = runHubTest {
        val provider = FakeDatabaseProvider(dbName = "bank-saved")
        val holder = ActiveDatabaseHolder()
        val filterStateHolder = FilterStateHolder()
        val savedHandle = SavedStateHandle(
            mapOf(
                "database_name" to "bank-saved",
                "selected_subject_ids" to listOf(1L, 2L),
                "excluded_subject_ids" to listOf(3L),
                "selected_system_ids" to listOf(10L),
                "excluded_system_ids" to listOf(20L),
                "status_filter" to "ANSWERED_INCORRECTLY:LATEST_ATTEMPT",
                "selected_difficulty_tiers" to listOf("DIFFICULT"),
            )
        )

        val viewModel = createViewModel(
            provider = provider,
            holder = holder,
            filterStateHolder = filterStateHolder,
            savedStateHandle = savedHandle,
        )
        provider.installInto(holder)
        advanceUntilIdle()

        assertEquals("bank-saved", viewModel.state.value.databaseName)
        assertEquals(setOf(1L, 2L), viewModel.state.value.selectedSubjectIds)
        assertEquals(setOf(3L), viewModel.state.value.excludedSubjectIds)
        assertEquals(setOf(10L), viewModel.state.value.selectedSystemIds)
        assertEquals(setOf(20L), viewModel.state.value.excludedSystemIds)
        assertEquals(
            StatusFilter(setOf(QuestionStatus.ANSWERED_INCORRECTLY), StatusScope.LATEST_ATTEMPT),
            viewModel.state.value.statusFilter
        )
        assertEquals(setOf(DifficultyTier.DIFFICULT), viewModel.state.value.selectedDifficultyTiers)
    }

    @Test
    fun databaseSwitchResetsFiltersOnlyWhenDatabaseNameChanges() = runHubTest {
        val providerA = FakeDatabaseProvider(dbName = "bank-a")
        val providerB = FakeDatabaseProvider(dbName = "bank-b")
        val holder = ActiveDatabaseHolder()
        val filterStateHolder = FilterStateHolder()

        val viewModel = createViewModel(
            provider = providerA,
            holder = holder,
            filterStateHolder = filterStateHolder,
        )
        providerA.installInto(holder)
        advanceUntilIdle()

        // Set custom filters on bank-a
        viewModel.applySelectedSubjects(setOf(1L), setOf(2L))
        advanceUntilIdle()
        assertEquals(setOf(1L), viewModel.state.value.selectedSubjectIds)
        assertEquals(setOf(2L), viewModel.state.value.excludedSubjectIds)

        // Reinstalling same providerA should NOT reset filters
        providerA.installInto(holder)
        advanceUntilIdle()
        assertEquals(setOf(1L), viewModel.state.value.selectedSubjectIds)
        assertEquals(setOf(2L), viewModel.state.value.excludedSubjectIds)

        // Switching to providerB MUST reset filters
        providerB.installInto(holder)
        advanceUntilIdle()
        assertEquals(emptySet(), viewModel.state.value.selectedSubjectIds)
        assertEquals(emptySet(), viewModel.state.value.excludedSubjectIds)
    }

    @Test
    fun applySelectedSubjectsPrunesBothSelectedAndExcludedSystems() = runHubTest {
        val provider = FakeDatabaseProvider(dbName = "bank-systems")
        provider.seededSystems = listOf(
            System(id = 10, name = "Cardiology", count = 5),
            System(id = 20, name = "Pulmonology", count = 5),
        )
        val holder = ActiveDatabaseHolder()
        val filterStateHolder = FilterStateHolder()

        val viewModel = createViewModel(
            provider = provider,
            holder = holder,
            filterStateHolder = filterStateHolder,
        )
        provider.installInto(holder)
        advanceUntilIdle()

        // Initially select system 10 (valid) and 99 (invalid), exclude system 20 (valid) and 88 (invalid)
        filterStateHolder.setSystems(setOf(10L, 99L), setOf(20L, 88L))
        advanceUntilIdle()
        assertEquals(setOf(10L, 99L), viewModel.state.value.selectedSystemIds)
        assertEquals(setOf(20L, 88L), viewModel.state.value.excludedSystemIds)

        // Applying subject 1 should prune systems: 99L and 88L must be removed
        viewModel.applySelectedSubjects(setOf(1L))
        advanceUntilIdle()

        assertEquals(setOf(10L), viewModel.state.value.selectedSystemIds)
        assertEquals(setOf(20L), viewModel.state.value.excludedSystemIds)
    }

    @Test
    fun changingDifficultyTiersDoesNotRequeryDifficultyCounts() = runHubTest {
        var difficultyQueryCount = 0
        val provider = FakeDatabaseProvider().apply {
            difficultyAvailable = true
            difficultyCountsProvider = { _, _, _, _, _ ->
                difficultyQueryCount++
                mapOf(DifficultyTier.EASY to 10)
            }
        }

        val filterStateHolder = FilterStateHolder()
        val holder = ActiveDatabaseHolder()
        val viewModel = createViewModel(provider, holder, filterStateHolder = filterStateHolder)
        provider.installInto(holder)
        advanceUntilIdle()

        val initialQueries = difficultyQueryCount
        assertTrue(initialQueries > 0, "Difficulty counts should be queried initially")

        // Change difficulty selection in ViewModel
        viewModel.setDifficultyFilters(setOf(DifficultyTier.EASY))
        advanceUntilIdle()

        assertEquals(
            initialQueries,
            difficultyQueryCount,
            "Changing difficulty tiers should not re-query difficulty counts",
        )
    }

    @Test
    fun restoreHistoryEntryIgnoresDuplicateCallWhileInFlight() = runHubTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val holder = ActiveDatabaseHolder()
        val vm = createViewModel(FakeDatabaseProvider(), holder)
        val entry = com.medqb.app.shared.data.QuizSessionRepository.QuizSession(
            id = "s1",
            databaseName = "bank",
            selectedSubjectIds = emptyList(),
            selectedSystemIds = emptyList(),
            statusFilter = StatusFilter.ALL,
            currentQuestionIndex = 0,
        )
        var failureCount = 0
        vm.restoreHistoryEntry(
            entry = entry,
            onSuccess = {},
            onFailure = {
                failureCount++
                gate.await()
            },
        )
        runCurrent()
        assertTrue(vm.isRestoringHistory.value)
        vm.restoreHistoryEntry(
            entry = entry,
            onSuccess = {},
            onFailure = {
                failureCount++
            },
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, failureCount)
        assertFalse(vm.isRestoringHistory.value)
    }
}
