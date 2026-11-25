package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessStarted
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.platform.rememberDropUnlessStarted
import com.medqb.app.shared.ui.screens.DatabaseSelectionScreen

@Composable
fun DatabaseSelectionEntry(
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
) {
    val dbVM = viewModel {
        graph.createDatabaseSelectionViewModel()
    }
    val databases by dbVM.availableDatabases.collectAsStateWithLifecycle()
    val isLoading by dbVM.isLoading.collectAsStateWithLifecycle()
    val errorMessage by dbVM.errorMessage.collectAsStateWithLifecycle()

    DatabaseSelectionScreen(
        databases = databases,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onRefreshDatabases = { dbVM.refreshDatabases() },
        onDatabaseSelected = rememberDropUnlessStarted { dbName ->
            if (navigator.currentRoute == MedQBRoutes.DatabaseSelection) {
                workflow.onDatabaseSelected(dbName)
                navigator.navigateTo(MedQBRoutes.Filter)
            }
        },
        onOpenSettings = dropUnlessStarted {
            navigator.navigateIfCurrent(MedQBRoutes.DatabaseSelection, MedQBRoutes.Settings)
        },
    )
}
