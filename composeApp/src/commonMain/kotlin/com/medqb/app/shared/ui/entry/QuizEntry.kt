package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.screens.quiz.QuizRoot
import com.medqb.app.shared.viewmodel.QuizViewModel
import kotlinx.coroutines.flow.first

@Composable
fun QuizEntry(
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
    mediaHandler: MediaHandler,
    onReturnToFilter: () -> Unit,
) {
    val quizVM = viewModel<QuizViewModel>(
        factory = viewModelFactory {
            initializer {
                graph.createQuizViewModel(
                    createSavedStateHandle()
                )
            }
        }
    )

    LaunchedEffect(quizVM) {
        quizVM.state.first { it.databaseName.isNotEmpty() }
        if (quizVM.state.value.questionIds.isEmpty()) {
            quizVM.loadFilteredQuestionIds(startFromBeginning = true)
        }
    }

    QuizRoot(
        viewModel = quizVM,
        mediaHandler = mediaHandler,
        onNavigateBack = dropUnlessResumed {
            onReturnToFilter()
        },
        onOpenSettingsScreen = dropUnlessResumed {
            navigator.navigateTo(MedQBRoutes.Settings)
        }
    )
}
