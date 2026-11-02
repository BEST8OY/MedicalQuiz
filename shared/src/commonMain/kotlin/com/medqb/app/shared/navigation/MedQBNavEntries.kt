package com.medqb.app.shared.navigation

import androidx.compose.animation.ContentTransform
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
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.ui.entry.DatabaseSelectionEntry
import com.medqb.app.shared.ui.entry.FilterEntry
import com.medqb.app.shared.ui.entry.HtmlViewerEntry
import com.medqb.app.shared.ui.entry.MediaViewerEntry
import com.medqb.app.shared.ui.entry.QuizEntry
import com.medqb.app.shared.ui.entry.SettingsEntry
import com.medqb.app.shared.ui.media.MediaHandler

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
    snackbarHostState: SnackbarHostState,
): (NavKey) -> NavEntry<NavKey> {
    val motionScheme = MaterialTheme.motionScheme
    return remember(
        graph,
        workflow,
        navigator,
        mediaHandler,
        snackbarHostState,
        motionScheme,
    ) {
        val modalEnterTransition: () -> ContentTransform = {
            (slideInVertically(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialOffsetY = { (it * 0.12f).toInt() }
            ) + scaleIn(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialScale = 0.94f
            ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                (scaleOut(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    targetScale = 0.96f
                ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
        }

        val modalPopTransition: () -> ContentTransform = {
            (scaleIn(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialScale = 0.96f
            ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                (slideOutVertically(
                    animationSpec = motionScheme.fastSpatialSpec(),
                    targetOffsetY = { (it * 0.12f).toInt() }
                ) + scaleOut(
                    animationSpec = motionScheme.fastSpatialSpec(),
                    targetScale = 0.94f
                ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
        }

        val mediaViewerFadeTransition: () -> ContentTransform = {
            fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                fadeOut(animationSpec = motionScheme.fastEffectsSpec())
        }

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
                    navigator = navigator,
                    mediaHandler = mediaHandler,
                )
            }

            entry<MedQBRoutes.Settings>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) { modalEnterTransition() }
                    put(NavDisplay.PopTransitionKey) { modalPopTransition() }
                    put(NavDisplay.PredictivePopTransitionKey) { modalPopTransition() }
                }
            ) {
                SettingsEntry(
                    graph = graph,
                    navigator = navigator,
                )
            }

            entry<MedQBRoutes.MediaViewer>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) { mediaViewerFadeTransition() }
                    put(NavDisplay.PopTransitionKey) { mediaViewerFadeTransition() }
                    put(NavDisplay.PredictivePopTransitionKey) { mediaViewerFadeTransition() }
                }
            ) { key ->
                MediaViewerEntry(
                    key = key,
                    graph = graph,
                    navigator = navigator,
                    mediaHandler = mediaHandler,
                )
            }

            entry<MedQBRoutes.HtmlViewer>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) { modalEnterTransition() }
                    put(NavDisplay.PopTransitionKey) { modalPopTransition() }
                    put(NavDisplay.PredictivePopTransitionKey) { modalPopTransition() }
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
