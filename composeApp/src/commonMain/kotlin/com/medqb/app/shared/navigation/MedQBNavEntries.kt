package com.medqb.app.shared.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun rememberMedQBNavEntries(
    graph: AppGraph,
    workflow: AppWorkflowHandle,
    navigator: AppNavigator,
    mediaHandler: MediaHandler,
    mediaDescriptionsFlow: MutableStateFlow<Map<String, MediaDescription>>,
    snackbarHostState: SnackbarHostState,
    onReturnQuizToFilter: () -> Unit,
): (NavKey) -> NavEntry<NavKey> {
    val motionScheme = MaterialTheme.motionScheme
    return remember(
        graph,
        workflow,
        navigator,
        mediaHandler,
        mediaDescriptionsFlow,
        snackbarHostState,
        onReturnQuizToFilter,
        motionScheme,
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

            entry<MedQBRoutes.Settings>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) {
                        (fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                            scaleIn(animationSpec = motionScheme.defaultSpatialSpec(), initialScale = 0.96f)) togetherWith
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                    }
                    put(NavDisplay.PopTransitionKey) {
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                            (fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.96f))
                    }
                    put(NavDisplay.PredictivePopTransitionKey) {
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                            (fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.96f))
                    }
                }
            ) {
                SettingsEntry(
                    graph = graph,
                    navigator = navigator,
                )
            }

            entry<MedQBRoutes.MediaViewer>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) {
                        (fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                            scaleIn(animationSpec = motionScheme.slowSpatialSpec(), initialScale = 0.92f)) togetherWith
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                    }
                    put(NavDisplay.PopTransitionKey) {
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                            (fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.92f))
                    }
                    put(NavDisplay.PredictivePopTransitionKey) {
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                            (fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.92f))
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

            entry<MedQBRoutes.HtmlViewer>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) {
                        (slideInVertically(
                            animationSpec = motionScheme.defaultSpatialSpec(),
                            initialOffsetY = { (it * 0.25f).toInt() }
                        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                    }
                    put(NavDisplay.PopTransitionKey) {
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                            (slideOutVertically(
                                animationSpec = motionScheme.defaultSpatialSpec(),
                                targetOffsetY = { (it * 0.25f).toInt() }
                            ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
                    }
                    put(NavDisplay.PredictivePopTransitionKey) {
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                            (slideOutVertically(
                                animationSpec = motionScheme.defaultSpatialSpec(),
                                targetOffsetY = { (it * 0.25f).toInt() }
                            ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
                    }
                }
            ) { key ->
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
