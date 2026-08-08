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
import com.medqb.app.shared.navigation.QuizLaunchSource

private val AppWorkflowStateSaver = Saver<AppWorkflowState, List<Any?>>(
    save = { state ->
        listOf(
            state.selectedDatabase,
            state.initializedDatabase,
            state.pendingLaunchSource?.name,
            state.activeQuizLaunchSource.name,
            state.requestedFilterPane?.name,
        )
    },
    restore = { list ->
        AppWorkflowState(
            selectedDatabase = list[0] as String?,
            initializedDatabase = list[1] as String?,
            pendingLaunchSource = (list[2] as String?)?.let { QuizLaunchSource.valueOf(it) },
            activeQuizLaunchSource = QuizLaunchSource.valueOf(list[3] as String),
            requestedFilterPane = (list[4] as String?)?.let { RequestedFilterPane.valueOf(it) },
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
        workflowState.pendingLaunchSource,
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

    val onStandardQuizLaunchPrepared = remember(workflowCoordinator) {
        { ->
            workflowState = workflowCoordinator.standardQuizLaunchPrepared(workflowState)
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

    val onQuizReturn = remember(workflowCoordinator) {
        { ->
            workflowState = workflowCoordinator.quizReturnedToFilter(workflowState)
            val targetPane = workflowState.requestedFilterPane
            workflowState = workflowState.copy(requestedFilterPane = null)
            targetPane
        }
    }

    return remember(
        workflowState,
        onDatabaseSelected,
        onDatabaseSelectionRequested,
        onStandardQuizLaunchPrepared,
        onHistoryLaunchPrepared,
        onFilterSubjectsSync,
        onQuizReturn,
    ) {
        AppWorkflowHandle(
            state = workflowState,
            onDatabaseSelected = onDatabaseSelected,
            onDatabaseSelectionRequested = onDatabaseSelectionRequested,
            onStandardQuizLaunchPrepared = onStandardQuizLaunchPrepared,
            onHistoryLaunchPrepared = onHistoryLaunchPrepared,
            onFilterSubjectsSync = onFilterSubjectsSync,
            onQuizReturn = onQuizReturn,
        )
    }
}

class AppWorkflowHandle(
    val state: AppWorkflowState,
    val onDatabaseSelected: (String) -> Unit,
    val onDatabaseSelectionRequested: () -> Unit,
    val onStandardQuizLaunchPrepared: () -> Unit,
    val onHistoryLaunchPrepared: (String) -> Unit,
    val onFilterSubjectsSync: (Set<Long>, Set<Long>, PerformanceFilter) -> Unit,
    val onQuizReturn: () -> RequestedFilterPane?,
)
