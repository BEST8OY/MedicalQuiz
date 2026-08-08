package com.medqb.app.shared.ui.entry

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.ui.screens.filter.FilterHubScreen
import com.medqb.app.shared.ui.screens.filter.FilterPane
import com.medqb.app.shared.viewmodel.FilterHubViewModel
import androidx.compose.runtime.LaunchedEffect

@Composable
fun FilterEntry(
    route: MedQBRoutes.Filter,
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
    snackbarHostState: SnackbarHostState,
) {
    val filterVM = viewModel<FilterHubViewModel>(
        factory = viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                route.initialPaneName?.let { handle["activePane"] = it }
                graph.createFilterHubViewModel(handle)
            }
        }
    )

    LaunchedEffect(route.initialPaneName) {
        route.initialPaneName?.let { paneName ->
            runCatching { FilterPane.valueOf(paneName) }.getOrNull()?.let { pane ->
                filterVM.setActivePane(pane)
            }
        }
    }

    val onStartQuiz = dropUnlessResumed {
        snackbarHostState.currentSnackbarData?.dismiss()
        workflow.onStandardQuizLaunchPrepared()
        navigator.navigateTo(MedQBRoutes.Quiz())
    }

    val onHistorySelected = remember(filterVM, workflow, navigator, graph, snackbarHostState) {
        { entry: QuizSessionRepository.QuizSession ->
            snackbarHostState.currentSnackbarData?.dismiss()
            filterVM.restoreHistoryEntry(
                entry = entry,
                onSuccess = { matchingDatabase ->
                    workflow.onFilterSubjectsSync(
                        entry.selectedSubjectIds.toSet(),
                        entry.selectedSystemIds.toSet(),
                        entry.performanceFilter,
                    )
                    workflow.onHistoryLaunchPrepared(matchingDatabase)
                    navigator.navigateTo(
                        MedQBRoutes.Quiz(
                            sessionId = entry.id,
                            entryName = entry.entryName,
                            initialQuestionIndex = entry.currentQuestionIndex,
                            isLoggingEnabled = entry.isLoggingEnabled,
                            submissionMode = entry.submissionMode.name,
                        )
                    )
                },
                onFailure = {
                    graph.snackbarDispatcher.emitSnackbar("Database files for this entry could not be found.")
                }
            )
        }
    }

    FilterHubScreen(
        viewModel = filterVM,
        onStartQuiz = onStartQuiz,
        onHistorySelected = onHistorySelected,
        onLoggingToggle = { graph.settingsRepository.setLoggingEnabled(it) },
        onSubmissionModeToggle = { graph.settingsRepository.setSubmissionMode(it) },
        onShowSnackbar = { message -> graph.snackbarDispatcher.emitSnackbar(message) },
        onDismissSnackbar = { snackbarHostState.currentSnackbarData?.dismiss() },
    )
}
