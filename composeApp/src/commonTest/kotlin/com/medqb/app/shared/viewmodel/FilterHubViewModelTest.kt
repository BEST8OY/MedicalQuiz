package com.medqb.app.shared.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.domain.ApplyFiltersUseCase
import com.medqb.app.shared.orchestration.AppHistoryCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.medqb.app.shared.data.models.System
import com.medqb.app.shared.utils.Resource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
                activeDatabaseHolder = ActiveDatabaseHolder(),
            ),
            sessionRepository = sessionRepository,
            ioDispatcher = StandardTestDispatcher(scheduler),
        )
    }

    @Test
    fun previewCountTracksPerformanceFilterAndSelectionMirrorsHolder() = runHubTest {
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

        filterStateHolder.updatePerformanceFilter(PerformanceFilter.LAST_CORRECT)
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.previewQuestionCount)
        assertEquals(PerformanceFilter.LAST_CORRECT, viewModel.state.value.performanceFilter)
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

        viewModel.setPerformanceFilter(PerformanceFilter.EVER_CORRECT)
        filterStateHolder.updateSubjectIds(setOf(1L, 2L))
        advanceUntilIdle()
        assertEquals(PerformanceFilter.EVER_CORRECT, viewModel.state.value.performanceFilter)
        // SavedStateHandle mirrors the holder reactively.
        assertEquals(listOf(1L, 2L), savedStateHandle.get<List<Long>>("selected_subject_ids"))
        assertEquals("EVER_CORRECT", savedStateHandle.get<String>("performance_filter"))

        viewModel.clearAllFilters()
        advanceUntilIdle()

        assertEquals(emptySet(), filterStateHolder.selectedSubjectIds.value)
        assertEquals(emptySet(), filterStateHolder.selectedSystemIds.value)
        assertEquals(PerformanceFilter.ALL, filterStateHolder.performanceFilter.value)
        assertEquals(emptySet(), viewModel.state.value.selectedSubjectIds)
        assertEquals(PerformanceFilter.ALL, viewModel.state.value.performanceFilter)
        assertEquals(emptyList(), savedStateHandle.get<List<Long>>("selected_subject_ids"))
        assertEquals("ALL", savedStateHandle.get<String>("performance_filter"))
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

        viewModel.setPerformanceFilter(PerformanceFilter.EVER_INCORRECT)
        filterStateHolder.updateSubjectIds(setOf(9L))
        advanceUntilIdle()
        assertEquals("EVER_INCORRECT", savedStateHandle.get<String>("performance_filter"))

        // Simulates an out-of-ViewModel reset (e.g. AppWorkflowCoordinator on db select):
        // with reactive persistence it must still reach SavedStateHandle.
        filterStateHolder.reset()
        advanceUntilIdle()

        assertEquals(emptyList(), savedStateHandle.get<List<Long>>("selected_subject_ids"))
        assertEquals("ALL", savedStateHandle.get<String>("performance_filter"))
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

        // Calling fetchSystemsForSubjects(listOf(99L)) should NOT mutate filterStateHolder
        viewModel.fetchSystemsForSubjects(listOf(99L))
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
}
