package com.medqb.app.shared.orchestration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter

@Composable
fun rememberAppWorkflow(
    workflowCoordinator: AppWorkflowCoordinator,
    filterStateHolder: FilterStateHolder,
    userDataManager: UserDataManager,
): AppWorkflowHandle {
    val selectedDatabaseState = rememberSaveable { mutableStateOf<String?>(null) }
    var initializedDatabase by remember { mutableStateOf<String?>(null) }

    val workflowState = AppWorkflowState(
        selectedDatabase = selectedDatabaseState.value,
        initializedDatabase = initializedDatabase,
    )

    LaunchedEffect(
        selectedDatabaseState.value,
        initializedDatabase,
    ) {
        val decision = workflowCoordinator.handleDatabaseSelection(workflowState, userDataManager)
        if (decision != null) {
            val updated = workflowCoordinator.applyDatabaseSelectionDecision(workflowState, decision)
            selectedDatabaseState.value = updated.selectedDatabase
            initializedDatabase = updated.initializedDatabase
        }
    }

    val onDatabaseSelected = remember(workflowCoordinator) {
        { dbName: String ->
            val updated = workflowCoordinator.databaseSelected(
                AppWorkflowState(selectedDatabaseState.value, initializedDatabase),
                dbName,
            )
            selectedDatabaseState.value = updated.selectedDatabase
            initializedDatabase = updated.initializedDatabase
        }
    }

    val onDatabaseSelectionRequested = remember(workflowCoordinator) {
        { ->
            val updated = workflowCoordinator.databaseSelectionRequested(
                AppWorkflowState(selectedDatabaseState.value, initializedDatabase),
            )
            selectedDatabaseState.value = updated.selectedDatabase
            initializedDatabase = updated.initializedDatabase
        }
    }

    val onHistoryLaunchPrepared = remember(workflowCoordinator) {
        { matchingDatabase: String ->
            val updated = workflowCoordinator.historyLaunchPrepared(
                AppWorkflowState(selectedDatabaseState.value, initializedDatabase),
                matchingDatabase,
            )
            selectedDatabaseState.value = updated.selectedDatabase
            initializedDatabase = updated.initializedDatabase
        }
    }

    val onFilterSubjectsSync = remember(filterStateHolder) {
        { subjectIds: Set<Long>, excludedSubjectIds: Set<Long>, systemIds: Set<Long>, excludedSystemIds: Set<Long>, statusFilter: StatusFilter, difficultyTiers: Set<DifficultyTier> ->
            filterStateHolder.setSubjects(subjectIds, excludedSubjectIds)
            filterStateHolder.setSystems(systemIds, excludedSystemIds)
            filterStateHolder.updateStatusFilter(statusFilter)
            filterStateHolder.updateDifficultyTiers(difficultyTiers)
        }
    }

    return remember(
        selectedDatabaseState,
        onDatabaseSelected,
        onDatabaseSelectionRequested,
        onHistoryLaunchPrepared,
        onFilterSubjectsSync,
    ) {
        AppWorkflowHandle(
            selectedDatabase = selectedDatabaseState,
            onDatabaseSelected = onDatabaseSelected,
            onDatabaseSelectionRequested = onDatabaseSelectionRequested,
            onHistoryLaunchPrepared = onHistoryLaunchPrepared,
            onFilterSubjectsSync = onFilterSubjectsSync,
        )
    }
}

class AppWorkflowHandle(
    val selectedDatabase: State<String?>,
    val onDatabaseSelected: (String) -> Unit,
    val onDatabaseSelectionRequested: () -> Unit,
    val onHistoryLaunchPrepared: (String) -> Unit,
    val onFilterSubjectsSync: (
        subjectIds: Set<Long>,
        excludedSubjectIds: Set<Long>,
        systemIds: Set<Long>,
        excludedSystemIds: Set<Long>,
        statusFilter: StatusFilter,
        difficultyTiers: Set<DifficultyTier>,
    ) -> Unit,
)
