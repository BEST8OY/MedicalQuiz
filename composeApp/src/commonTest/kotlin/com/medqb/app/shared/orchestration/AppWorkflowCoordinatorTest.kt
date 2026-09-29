package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.viewmodel.FakeAppStartupCoordinator
import com.medqb.app.shared.viewmodel.FakeUserDataManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppWorkflowCoordinatorTest {

    private val startupCoordinator = FakeAppStartupCoordinator()
    private val filterStateHolder = FilterStateHolder()
    private val coordinator = AppWorkflowCoordinator(startupCoordinator, filterStateHolder)
    private val fakeUserDataManager = FakeUserDataManager()

    @Test
    fun initialStateReturnsDefaultWorkflowState() {
        val initial = coordinator.initialState()
        assertNull(initial.selectedDatabase)
        assertNull(initial.initializedDatabase)
    }

    @Test
    fun databaseSelectedResetsFiltersAndSetsTargetDatabase() {
        filterStateHolder.updateSubjectIds(setOf(1L, 2L))
        filterStateHolder.updateSystemIds(setOf(3L))

        val state = coordinator.databaseSelected(coordinator.initialState(), "Cardio.db")

        assertEquals("Cardio.db", state.selectedDatabase)
        assertNull(state.initializedDatabase)
        // Verify filters are reset
        assertTrue(filterStateHolder.selectedSubjectIds.value.isEmpty())
        assertTrue(filterStateHolder.selectedSystemIds.value.isEmpty())
    }

    @Test
    fun databaseSelectionRequestedClearsSelections() {
        val state = AppWorkflowState(
            selectedDatabase = "Cardio.db",
            initializedDatabase = "Cardio.db",
        )
        val resetState = coordinator.databaseSelectionRequested(state)

        assertNull(resetState.selectedDatabase)
        assertNull(resetState.initializedDatabase)
    }

    @Test
    fun applyDatabaseSelectionDecisionUpdatesInitializedDatabase() {
        val state = AppWorkflowState(selectedDatabase = "Renal.db")
        val decision = DatabaseSelectionDecision("Renal.db")

        val updated = coordinator.applyDatabaseSelectionDecision(state, decision)
        assertEquals("Renal.db", updated.initializedDatabase)
        assertEquals("Renal.db", updated.selectedDatabase)
    }

    @Test
    fun historyLaunchPreparedSetsSelectedDatabase() {
        val state = coordinator.initialState()
        val prepared = coordinator.historyLaunchPrepared(state, "HistoryBank.db")

        assertEquals("HistoryBank.db", prepared.selectedDatabase)
    }

    @Test
    fun handleDatabaseSelectionReturnsNullWhenNoDatabaseSelected() = runTest {
        val state = coordinator.initialState()
        val decision = coordinator.handleDatabaseSelection(state, fakeUserDataManager)

        assertNull(decision)
        assertNull(startupCoordinator.lastSelectedDb)
    }

    @Test
    fun handleDatabaseSelectionDelegatesToStartupCoordinator() = runTest {
        val state = AppWorkflowState(selectedDatabase = "USMLE.db")
        val decision = coordinator.handleDatabaseSelection(state, fakeUserDataManager)

        assertNotNull(decision)
        assertEquals("USMLE.db", decision.initializedDatabase)
        assertEquals("USMLE.db", startupCoordinator.lastSelectedDb)
    }
}
