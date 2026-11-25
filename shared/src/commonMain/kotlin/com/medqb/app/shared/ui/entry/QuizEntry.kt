package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    onOpenMedia: (files: List<String>, startIndex: Int) -> Unit,
    onOpenHtml: (fileName: String) -> Unit,
) {
    val quizVM = viewModel {
        graph.createQuizViewModel(createSavedStateHandle(), route)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaHandler = remember(lifecycleOwner, onOpenMedia, onOpenHtml) {
        MediaHandler(
            onOpenMedia = { files, index ->
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    onOpenMedia(files, index)
                }
            },
            onOpenHtml = { fileName ->
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    onOpenHtml(fileName)
                }
            },
        )
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
