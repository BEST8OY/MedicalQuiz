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
    route: MedQBRoutes.Quiz,
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
    mediaHandler: MediaHandler,
    onReturnToFilter: () -> Unit,
) {
    val quizVM = viewModel<QuizViewModel>(
        key = route.sessionId.ifEmpty { "default_quiz" },
        factory = viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                if (route.sessionId.isNotBlank()) handle["sessionId"] = route.sessionId
                if (route.entryName.isNotBlank()) handle["entryName"] = route.entryName
                handle["currentQuestionIndex"] = route.initialQuestionIndex
                route.isLoggingEnabled?.let { handle["isLoggingEnabled"] = it }
                route.submissionMode?.let { handle["submissionMode"] = it }
                graph.createQuizViewModel(handle)
            }
        }
    )

    QuizRoot(
        viewModel = quizVM,
        mediaHandler = mediaHandler,
        onNavigateBack = {
            onReturnToFilter()
        },
        onOpenSettingsScreen = dropUnlessResumed {
            navigator.navigateTo(MedQBRoutes.Settings)
        }
    )
}
