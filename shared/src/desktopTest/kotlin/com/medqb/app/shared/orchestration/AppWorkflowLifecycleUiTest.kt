package com.medqb.app.shared.orchestration

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.viewmodel.FakeAppStartupCoordinator
import com.medqb.app.shared.viewmodel.FakeUserDataManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class AppWorkflowLifecycleUiTest {

    @Test
    fun selectingDatabaseRunsInitOnceAndReturningToDatabaseSelectionClosesIt() = runComposeUiTest {
        val startupCoordinator = FakeAppStartupCoordinator()
        val workflowCoordinator = AppWorkflowCoordinator(startupCoordinator, FilterStateHolder())
        val userDataManager = FakeUserDataManager()
        val backStack = mutableStateListOf<NavKey>(MedQBRoutes.DatabaseSelection)
        val navigator = AppNavigator(backStack)
        lateinit var workflow: AppWorkflowHandle

        setContent {
            workflow = rememberAppWorkflow(
                workflowCoordinator = workflowCoordinator,
                filterStateHolder = FilterStateHolder(),
                userDataManager = userDataManager,
            )

            NavDisplay(
                backStack = backStack,
                onBack = { navigator.navigateBack() },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                entryProvider = entryProvider {
                    entry<MedQBRoutes.DatabaseSelection> {
                        LifecycleResumeEffect(Unit) {
                            workflow.onDatabaseSelectionRequested()
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

        // Pop back to DatabaseSelection via navigator (simulating system/gesture back).
        // Once the pop settles and DatabaseSelection resumes, selection clears and DB closes.
        runOnUiThread {
            navigator.navigateBack()
        }
        waitForIdle()

        assertNull(workflow.selectedDatabase.value)
        assertEquals(1, startupCoordinator.handleSelectionCount)
        assertEquals(2, startupCoordinator.closeCount)
    }
}
