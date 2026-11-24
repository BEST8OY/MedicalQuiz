package com.medqb.app.shared.ui.entry

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.platform.rememberDropUnlessResumed
import com.medqb.app.shared.ui.screens.filter.FilterHubScreen

@Composable
fun FilterEntry(
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
    snackbarHostState: SnackbarHostState,
) {
    val filterVM = viewModel {
        graph.createFilterHubViewModel(createSavedStateHandle())
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val onStartQuiz = dropUnlessResumed {
        snackbarHostState.currentSnackbarData?.dismiss()
        navigator.navigateTo(MedQBRoutes.Quiz())
    }

    val onHistorySelected = rememberDropUnlessResumed<QuizSessionRepository.QuizSession> { entry ->
        snackbarHostState.currentSnackbarData?.dismiss()
        filterVM.restoreHistoryEntry(
            entry = entry,
            onSuccess = { matchingDatabase ->
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    return@restoreHistoryEntry
                }
                workflow.onFilterSubjectsSync(
                    entry.selectedSubjectIds.toSet(),
                    entry.excludedSubjectIds.toSet(),
                    entry.selectedSystemIds.toSet(),
                    entry.excludedSystemIds.toSet(),
                    entry.statusFilter,
                    entry.selectedDifficultyTiers,
                )
                workflow.onHistoryLaunchPrepared(matchingDatabase)
                navigator.navigateTo(
                    MedQBRoutes.Quiz(
                        sessionId = entry.id,
                        entryName = entry.entryName,
                        initialQuestionIndex = entry.currentQuestionIndex,
                        isLoggingEnabled = entry.isLoggingEnabled,
                        submissionMode = entry.submissionMode,
                    )
                )
            },
            onFailure = {
                graph.snackbarDispatcher.emitSnackbar("Database files for this entry could not be found.")
            }
        )
    }

    FilterHubScreen(
        viewModel = filterVM,
        onStartQuiz = onStartQuiz,
        onHistorySelected = onHistorySelected,
        onShowSnackbar = { message -> graph.snackbarDispatcher.emitSnackbar(message) },
        onDismissSnackbar = { snackbarHostState.currentSnackbarData?.dismiss() },
    )
}
