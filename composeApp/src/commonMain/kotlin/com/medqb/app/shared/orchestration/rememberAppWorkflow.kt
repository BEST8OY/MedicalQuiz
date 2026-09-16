package com.medqb.app.shared.orchestration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.data.database.PerformanceFilter

private val AppWorkflowStateSaver = Saver<AppWorkflowState, List<Any?>>(
    save = { state ->
        listOf(
            state.selectedDatabase,
            state.initializedDatabase,
        )
    },
    restore = { list ->
        AppWorkflowState(
            selectedDatabase = list[0] as String?,
            initializedDatabase = list[1] as String?,
        )
    }
)

@Composable
fun rememberAppWorkflow(
    workflowCoordinator: AppWorkflowCoordinator,
    filterStateHolder: FilterStateHolder,
    userDataManager: UserDataManager,
): AppWorkflowHandle {
    var workflowState by rememberSaveable(stateSaver = AppWorkflowStateSaver) {
        mutableStateOf(workflowCoordinator.initialState())
    }

    LaunchedEffect(
        workflowState.selectedDatabase,
        workflowState.initializedDatabase,
    ) {
        val decision = workflowCoordinator.handleDatabaseSelection(workflowState, userDataManager)
        if (decision != null) {
            workflowState = workflowCoordinator.applyDatabaseSelectionDecision(
                workflowState, decision
            )
        }
    }

    val onDatabaseSelected = remember(workflowCoordinator, filterStateHolder) {
        { dbName: String ->
            workflowState = workflowCoordinator.databaseSelected(workflowState, dbName)
        }
    }

    val onDatabaseSelectionRequested = remember(workflowCoordinator) {
        { ->
            workflowState = workflowCoordinator.databaseSelectionRequested(workflowState)
        }
    }

    val onHistoryLaunchPrepared = remember(workflowCoordinator) {
        { matchingDatabase: String ->
            workflowState = workflowCoordinator.historyLaunchPrepared(
                workflowState, matchingDatabase
            )
        }
    }

    val onFilterSubjectsSync = remember(filterStateHolder) {
        { subjectIds: Set<Long>, systemIds: Set<Long>, perfFilter: PerformanceFilter ->
            filterStateHolder.updateSubjectIds(subjectIds)
            filterStateHolder.updateSystemIds(systemIds)
            filterStateHolder.updatePerformanceFilter(perfFilter)
        }
    }

    return remember(
        workflowState,
        onDatabaseSelected,
        onDatabaseSelectionRequested,
        onHistoryLaunchPrepared,
        onFilterSubjectsSync,
    ) {
        AppWorkflowHandle(
            state = workflowState,
            onDatabaseSelected = onDatabaseSelected,
            onDatabaseSelectionRequested = onDatabaseSelectionRequested,
            onHistoryLaunchPrepared = onHistoryLaunchPrepared,
            onFilterSubjectsSync = onFilterSubjectsSync,
        )
    }
}

class AppWorkflowHandle(
    val state: AppWorkflowState,
    val onDatabaseSelected: (String) -> Unit,
    val onDatabaseSelectionRequested: () -> Unit,
    val onHistoryLaunchPrepared: (String) -> Unit,
    val onFilterSubjectsSync: (Set<Long>, Set<Long>, PerformanceFilter) -> Unit,
)
