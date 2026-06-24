package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.ui.screens.FilterHubScreen
import com.medqb.app.shared.ui.screens.FilterPane
import com.medqb.app.shared.viewmodel.FilterViewModel

@Composable
fun FilterEntry(
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
) {
    val filterVM = viewModel<FilterViewModel>(
        factory = viewModelFactory {
            initializer {
                graph.createFilterViewModel(
                    createSavedStateHandle()
                )
            }
        }
    )

    val onStartQuiz = dropUnlessResumed {
        workflow.onStandardQuizLaunchPrepared()
        navigator.navigateTo(MedQBRoutes.Quiz)
    }

    FilterHubScreen(
        viewModel = filterVM,
        selectedPane = FilterPane.Filters,
        onPaneSelected = { pane ->
            when (pane) {
                FilterPane.Filters -> navigator.switchTo(MedQBRoutes.Filter)
                FilterPane.History -> navigator.switchTo(MedQBRoutes.History)
            }
        },
        onStartQuiz = onStartQuiz,
        onLoggingToggle = { graph.settingsRepository.setLoggingEnabled(it) },
        onSubmissionModeToggle = { graph.settingsRepository.setSubmissionMode(it) },
    )
}
