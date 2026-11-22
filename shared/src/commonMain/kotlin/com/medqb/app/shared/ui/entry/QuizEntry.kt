package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.screens.quiz.QuizRoot

@Composable
fun QuizEntry(
    route: MedQBRoutes.Quiz,
    graph: AppGraph,
    navigator: AppNavigator,
    mediaHandler: MediaHandler,
) {
    val quizVM = viewModel {
        graph.createQuizViewModel(createSavedStateHandle(), route)
    }

    QuizRoot(
        viewModel = quizVM,
        mediaHandler = mediaHandler,
        onNavigateBack = dropUnlessResumed {
            navigator.navigateBack()
        },
        onOpenSettingsScreen = dropUnlessResumed {
            navigator.navigateTo(MedQBRoutes.Settings)
        }
    )
}
