package com.medqb.app.shared.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.ui.entry.DatabaseSelectionEntry
import com.medqb.app.shared.ui.entry.FilterEntry
import com.medqb.app.shared.ui.entry.HtmlViewerEntry
import com.medqb.app.shared.ui.entry.MediaViewerEntry
import com.medqb.app.shared.ui.entry.QuizEntry
import com.medqb.app.shared.ui.entry.SettingsEntry
import com.medqb.app.shared.ui.media.MediaHandler
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Builds the Navigation 3 [entryProvider] mapping [MedQBRoutes] to their composable entries.
 *
 * Extracting this helper keeps the root `App` composable clean and modular.
 */
@Composable
fun rememberMedQBNavEntries(
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
    mediaHandler: MediaHandler,
    mediaDescriptionsFlow: MutableStateFlow<Map<String, MediaDescription>>,
    snackbarHostState: SnackbarHostState,
    onReturnQuizToFilter: () -> Unit,
): (NavKey) -> NavEntry<NavKey> {
    return remember(
        graph,
        workflow,
        navigator,
        mediaHandler,
        mediaDescriptionsFlow,
        snackbarHostState,
        onReturnQuizToFilter,
    ) {
        entryProvider<NavKey> {
            entry<MedQBRoutes.DatabaseSelection> {
                DatabaseSelectionEntry(
                    graph = graph,
                    workflow = workflow,
                    navigator = navigator,
                )
            }

            entry<MedQBRoutes.Filter> { route ->
                FilterEntry(
                    route = route,
                    graph = graph,
                    workflow = workflow,
                    navigator = navigator,
                    snackbarHostState = snackbarHostState,
                )
            }

            entry<MedQBRoutes.Quiz> { route ->
                QuizEntry(
                    route = route,
                    graph = graph,
                    workflow = workflow,
                    navigator = navigator,
                    mediaHandler = mediaHandler,
                    onReturnToFilter = onReturnQuizToFilter,
                )
            }

            entry<MedQBRoutes.Settings> {
                SettingsEntry(
                    graph = graph,
                    navigator = navigator,
                )
            }

            entry<MedQBRoutes.MediaViewer>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) {
                        fadeIn() togetherWith fadeOut()
                    }
                    put(NavDisplay.PopTransitionKey) {
                        fadeIn() togetherWith fadeOut()
                    }
                    put(NavDisplay.PredictivePopTransitionKey) {
                        fadeIn() togetherWith fadeOut()
                    }
                }
            ) { key ->
                MediaViewerEntry(
                    key = key,
                    graph = graph,
                    navigator = navigator,
                    mediaHandler = mediaHandler,
                    mediaDescriptionsFlow = mediaDescriptionsFlow,
                )
            }

            entry<MedQBRoutes.HtmlViewer> { key ->
                HtmlViewerEntry(
                    key = key,
                    graph = graph,
                    navigator = navigator,
                    mediaHandler = mediaHandler,
                )
            }
        }
    }
}
