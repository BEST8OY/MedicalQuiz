package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.ui.screens.DatabaseSelectionScreen
import com.medqb.app.shared.viewmodel.DatabaseSelectionViewModel

@Composable
fun DatabaseSelectionEntry(
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
) {
    val dbVM = viewModel<DatabaseSelectionViewModel>(
        factory = viewModelFactory {
            initializer {
                graph.createDatabaseSelectionViewModel()
            }
        }
    )
    val databases by dbVM.availableDatabases.collectAsStateWithLifecycle()
    val isLoading by dbVM.isLoading.collectAsStateWithLifecycle()
    val errorMessage by dbVM.errorMessage.collectAsStateWithLifecycle()

    DatabaseSelectionScreen(
        databases = databases,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onRefreshDatabases = { dbVM.refreshDatabases() },
        onDatabaseSelected = { dbName ->
            workflow.onDatabaseSelected(dbName)
            navigator.navigateTo(MedQBRoutes.Filter())
        },
        onOpenSettings = {
            navigator.navigateTo(MedQBRoutes.Settings)
        },
    )
}
