package com.medqb.app.shared.orchestration

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.ManagedDatabaseProvider
import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.viewmodel.FakeAppStartupCoordinator
import com.medqb.app.shared.viewmodel.FakeDatabaseProvider
import com.medqb.app.shared.viewmodel.FakeUserDataManager
import kotlinx.coroutines.CompletableDeferred
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class AppWorkflowLifecycleUiTest {

    @Test
    fun selectingDatabaseRunsInitOnceAndStaysOpenUntilPopSettles() = runComposeUiTest {
        val startupCoordinator = FakeAppStartupCoordinator()
        val filterStateHolder = FilterStateHolder()
        val workflowCoordinator = AppWorkflowCoordinator(startupCoordinator, filterStateHolder)
        val backStack = mutableStateListOf<NavKey>(MedQBRoutes.DatabaseSelection)
        val navigator = AppNavigator(backStack)
        lateinit var workflow: AppWorkflowHandle

        setContent {
            workflow = rememberAppWorkflow(
                workflowCoordinator = workflowCoordinator,
                filterStateHolder = filterStateHolder,
            )

            NavDisplay(
                backStack = backStack,
                onBack = { navigator.navigateBack() },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                entryProvider = entryProvider {
                    entry<MedQBRoutes.DatabaseSelection> {
                        val currentWorkflow by rememberUpdatedState(workflow)
                        LifecycleResumeEffect(Unit) {
                            currentWorkflow.onDatabaseSelectionRequested()
                            onPauseOrDispose { }
                        }
                        Box(modifier = Modifier.fillMaxSize())
                    }
                    entry<MedQBRoutes.Filter> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                },
            )
        }

        waitForIdle()
        assertNull(workflow.selectedDatabase.value)
        assertEquals(0, startupCoordinator.handleSelectionCount)
        assertEquals(1, startupCoordinator.closeCount)

        // Select a database and navigate to Filter.
        runOnUiThread {
            workflow.onDatabaseSelected("Cardio.db")
            navigator.navigateTo(MedQBRoutes.Filter)
        }
        waitForIdle()

        assertEquals("Cardio.db", workflow.selectedDatabase.value)
        assertEquals(1, startupCoordinator.handleSelectionCount)
        assertEquals("Cardio.db", startupCoordinator.lastSelectedDb)
        assertEquals(1, startupCoordinator.closeCount)

        // Start popping back to DatabaseSelection: mid-transition, DatabaseSelection is not yet RESUMED,
        // so the DB selection must remain active until the pop settles.
        mainClock.autoAdvance = false
        runOnUiThread {
            navigator.navigateBack()
        }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        assertEquals("Cardio.db", workflow.selectedDatabase.value)
        assertEquals(1, startupCoordinator.closeCount)

        // Advance until the pop animation settles and DatabaseSelection becomes RESUMED.
        repeat(40) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }

        assertNull(workflow.selectedDatabase.value)
        assertEquals(1, startupCoordinator.handleSelectionCount)
        assertEquals(2, startupCoordinator.closeCount)
    }

    @Test
    fun quickReselectAfterPopSerializesSlowCloseBeforeOpeningNextDatabase() = runComposeUiTest {
        val holder = ActiveDatabaseHolder()
        val closeGate = CompletableDeferred<Unit>()
        val events = Collections.synchronizedList(mutableListOf<String>())

        val startupCoordinator = object : AppStartupCoordinator(
            localContentRepository = LocalContentRepository(),
            activeDatabaseHolder = holder,
            userDataManager = FakeUserDataManager(),
        ) {
            override fun createDatabaseManager(dbPath: String, canonicalDbName: String): ManagedDatabaseProvider {
                val delegate = FakeDatabaseProvider(canonicalDbName)
                return object : ManagedDatabaseProvider, DatabaseProvider by delegate {
                    override suspend fun init() {
                        events += "init:$canonicalDbName"
                    }

                    override suspend fun closeDatabase() {
                        events += "close:start:$canonicalDbName"
                        if (canonicalDbName == "Cardio") {
                            closeGate.await()
                        }
                        events += "close:done:$canonicalDbName"
                    }
                }
            }
        }

        val filterStateHolder = FilterStateHolder()
        val workflowCoordinator = AppWorkflowCoordinator(startupCoordinator, filterStateHolder)
        val backStack = mutableStateListOf<NavKey>(MedQBRoutes.DatabaseSelection)
        val navigator = AppNavigator(backStack)
        lateinit var workflow: AppWorkflowHandle

        setContent {
            workflow = rememberAppWorkflow(
                workflowCoordinator = workflowCoordinator,
                filterStateHolder = filterStateHolder,
            )
            NavDisplay(
                backStack = backStack,
                onBack = { navigator.navigateBack() },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                entryProvider = entryProvider {
                    entry<MedQBRoutes.DatabaseSelection> {
                        val currentWorkflow by rememberUpdatedState(workflow)
                        LifecycleResumeEffect(Unit) {
                            currentWorkflow.onDatabaseSelectionRequested()
                            onPauseOrDispose { }
                        }
                        Box(modifier = Modifier.fillMaxSize())
                    }
                    entry<MedQBRoutes.Filter> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                },
            )
        }

        waitForIdle()
        runOnUiThread {
            workflow.onDatabaseSelected("Cardio.db")
            navigator.navigateTo(MedQBRoutes.Filter)
        }
        waitForIdle()
        assertEquals("Cardio", holder.activeDatabase.value?.name)

        // Pop back to DatabaseSelection, triggering slow async close of Cardio.db.
        mainClock.autoAdvance = false
        runOnUiThread { navigator.navigateBack() }
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            "close:start:Cardio" in events
        }

        // Immediately select Renal.db while Cardio.db close is still awaiting closeGate.
        runOnUiThread {
            workflow.onDatabaseSelected("Renal.db")
            navigator.navigateTo(MedQBRoutes.Filter)
        }
        repeat(5) { mainClock.advanceTimeByFrame() }

        // Renal must NOT have initialized while Cardio is still closing.
        assertEquals(false, "init:Renal" in events, "Renal must wait for Cardio close to complete, events=$events")

        // Complete the pending close and settle.
        closeGate.complete(Unit)
        mainClock.autoAdvance = true
        waitForIdle()

        val renalIdx = events.indexOf("init:Renal")
        val closeDoneIdx = events.indexOf("close:done:Cardio")
        assertNotNull(holder.activeDatabase.value)
        assertEquals("Renal", holder.activeDatabase.value?.name)
        assertEquals(true, closeDoneIdx in 0 until renalIdx, "Expected Cardio close to finish before Renal init, events=$events")
    }

    @Test
    fun recomposingWorkflowHandleDuringNavigationDoesNotClearSelectedDatabase() = runComposeUiTest {
        val startupCoordinator = FakeAppStartupCoordinator()
        val filterStateHolder = FilterStateHolder()
        val backStack = mutableStateListOf<NavKey>(MedQBRoutes.DatabaseSelection)
        val navigator = AppNavigator(backStack)
        var coordinatorEpoch by mutableIntStateOf(0)
        lateinit var workflow: AppWorkflowHandle

        setContent {
            // Simulate a workflowCoordinator identity change on recomposition when navigating.
            val workflowCoordinator = AppWorkflowCoordinator(startupCoordinator, filterStateHolder)
            @Suppress("UNUSED_EXPRESSION")
            coordinatorEpoch
            workflow = rememberAppWorkflow(
                workflowCoordinator = workflowCoordinator,
                filterStateHolder = filterStateHolder,
            )

            NavDisplay(
                backStack = backStack,
                onBack = { navigator.navigateBack() },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                entryProvider = entryProvider {
                    entry<MedQBRoutes.DatabaseSelection> {
                        val currentWorkflow by rememberUpdatedState(workflow)
                        LifecycleResumeEffect(Unit) {
                            currentWorkflow.onDatabaseSelectionRequested()
                            onPauseOrDispose { }
                        }
                        Box(modifier = Modifier.fillMaxSize())
                    }
                    entry<MedQBRoutes.Filter> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                },
            )
        }

        waitForIdle()
        assertNull(workflow.selectedDatabase.value)

        runOnUiThread {
            workflow.onDatabaseSelected("Cardio.db")
            coordinatorEpoch++
            navigator.navigateTo(MedQBRoutes.Filter)
        }
        waitForIdle()

        assertEquals("Cardio.db", workflow.selectedDatabase.value)
        assertEquals(1, startupCoordinator.handleSelectionCount)
        assertEquals("Cardio.db", startupCoordinator.lastSelectedDb)
    }
}
