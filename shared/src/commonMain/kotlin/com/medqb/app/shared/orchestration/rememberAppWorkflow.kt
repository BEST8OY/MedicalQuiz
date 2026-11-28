package com.medqb.app.shared.orchestration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.medqb.app.shared.data.FilterStateHolder
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter

@Composable
fun rememberAppWorkflow(
    workflowCoordinator: AppWorkflowCoordinator,
    filterStateHolder: FilterStateHolder,
): AppWorkflowHandle {
    val selectedDatabaseState = rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDatabaseState.value) {
        val dbName = selectedDatabaseState.value
        if (dbName == null) {
            workflowCoordinator.closeActiveDatabase()
        } else {
            workflowCoordinator.handleDatabaseSelection(dbName)
        }
    }

    val onDatabaseSelected = remember(workflowCoordinator) {
        { dbName: String ->
            selectedDatabaseState.value = workflowCoordinator.onDatabaseSelected(dbName)
        }
    }

    val onDatabaseSelectionRequested = remember(workflowCoordinator) {
        { ->
            selectedDatabaseState.value = workflowCoordinator.onDatabaseSelectionRequested()
        }
    }

    val onHistoryLaunchPrepared = remember(workflowCoordinator) {
        { matchingDatabase: String ->
            selectedDatabaseState.value = workflowCoordinator.onHistoryLaunchPrepared(matchingDatabase)
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
