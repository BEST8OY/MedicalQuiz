package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.viewmodel.FakeAppStartupCoordinator
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

    @Test
    fun onDatabaseSelectedResetsFiltersAndReturnsDatabaseName() {
        filterStateHolder.updateSubjectIds(setOf(1L, 2L))
        filterStateHolder.updateSystemIds(setOf(3L))

        val selected = coordinator.onDatabaseSelected("Cardio.db")

        assertEquals("Cardio.db", selected)
        assertTrue(filterStateHolder.selectedSubjectIds.value.isEmpty())
        assertTrue(filterStateHolder.selectedSystemIds.value.isEmpty())
    }

    @Test
    fun onDatabaseSelectionRequestedClearsSelection() {
        assertNull(coordinator.onDatabaseSelectionRequested())
    }

    @Test
    fun onHistoryLaunchPreparedReturnsMatchingDatabase() {
        assertEquals("HistoryBank.db", coordinator.onHistoryLaunchPrepared("HistoryBank.db"))
    }

    @Test
    fun handleDatabaseSelectionReturnsNullWhenNoDatabaseSelected() = runTest {
        val decision = coordinator.handleDatabaseSelection(null)

        assertNull(decision)
        assertNull(startupCoordinator.lastSelectedDb)
    }

    @Test
    fun handleDatabaseSelectionDelegatesToStartupCoordinator() = runTest {
        val decision = coordinator.handleDatabaseSelection("USMLE.db")

        assertNotNull(decision)
        assertEquals("USMLE.db", decision.initializedDatabase)
        assertEquals("USMLE.db", startupCoordinator.lastSelectedDb)
    }

    @Test
    fun closeActiveDatabaseDelegatesToStartupCoordinator() = runTest {
        coordinator.closeActiveDatabase()
        assertEquals(1, startupCoordinator.closeCount)
    }
}
