package com.medqb.app.shared.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseSelectionViewModelTest {

    private val scheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initializationLoadsAvailableDatabasesSuccessfully() = runTest(StandardTestDispatcher(scheduler)) {
        val coordinator = FakeAppStartupCoordinator(
            availableDatabasesResult = listOf("USMLE_Step1.db", "USMLE_Step2.db")
        )
        val viewModel = DatabaseSelectionViewModel(coordinator)

        assertTrue(viewModel.isLoading.value)
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.errorMessage.value)
        assertEquals(listOf("USMLE_Step1.db", "USMLE_Step2.db"), viewModel.availableDatabases.value)
        assertEquals(1, coordinator.initializedCount)
    }

    @Test
    fun initializationFailureSetsErrorMessage() = runTest(StandardTestDispatcher(scheduler)) {
        val coordinator = FakeAppStartupCoordinator(shouldFail = true)
        val viewModel = DatabaseSelectionViewModel(coordinator)

        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertTrue(viewModel.availableDatabases.value.isEmpty())
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value!!.contains("Failed to scan databases"))
    }

    @Test
    fun refreshDatabasesUpdatesDatabaseList() = runTest(StandardTestDispatcher(scheduler)) {
        val coordinator = FakeAppStartupCoordinator(
            availableDatabasesResult = listOf("Initial.db")
        )
        val viewModel = DatabaseSelectionViewModel(coordinator)
        advanceUntilIdle()
        assertEquals(listOf("Initial.db"), viewModel.availableDatabases.value)

        // Prepare new list and trigger refresh
        coordinator.availableDatabasesResult = listOf("Initial.db", "AddedLater.db")
        viewModel.refreshDatabases()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertEquals(listOf("Initial.db", "AddedLater.db"), viewModel.availableDatabases.value)
        assertEquals(1, coordinator.refreshCount)
    }

    @Test
    fun refreshFailurePreservesPreviousStateAndSetsError() = runTest(StandardTestDispatcher(scheduler)) {
        val coordinator = FakeAppStartupCoordinator(
            availableDatabasesResult = listOf("Initial.db")
        )
        val viewModel = DatabaseSelectionViewModel(coordinator)
        advanceUntilIdle()

        coordinator.shouldFail = true
        viewModel.refreshDatabases()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value!!.contains("Failed to refresh"))
    }
}
