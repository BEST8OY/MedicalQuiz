package com.medqb.app.shared.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.orchestration.AppWorkflowHandle
import com.medqb.app.shared.ui.entry.DatabaseSelectionEntry
import com.medqb.app.shared.ui.entry.FilterEntry
import com.medqb.app.shared.ui.entry.HtmlViewerEntry
import com.medqb.app.shared.ui.entry.MediaViewerEntry
import com.medqb.app.shared.ui.entry.QuizEntry
import com.medqb.app.shared.ui.entry.SettingsEntry
import com.medqb.app.shared.ui.theme.exitSpatialSpec

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
    onOpenMedia: (files: List<String>, startIndex: Int) -> Unit,
    onOpenHtml: (fileName: String) -> Unit,
): (NavKey) -> NavEntry<NavKey> {
    val motionScheme = MaterialTheme.motionScheme
    return remember(
        graph,
        workflow,
        navigator,
        onOpenMedia,
        onOpenHtml,
        motionScheme,
    ) {
        val modalEnterTransition: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform = {
            (slideInVertically(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialOffsetY = { (it * 0.12f).toInt() }
            ) + scaleIn(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialScale = 0.94f
            ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                (scaleOut(
                    animationSpec = exitSpatialSpec(),
                    targetScale = 0.96f
                ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
        }

        val modalPopTransition: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform = {
            (scaleIn(
                animationSpec = motionScheme.fastSpatialSpec(),
                initialScale = 0.96f
            ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                (slideOutVertically(
                    animationSpec = exitSpatialSpec(stiffness = Spring.StiffnessMedium),
                    targetOffsetY = { (it * 0.12f).toInt() }
                ) + scaleOut(
                    animationSpec = exitSpatialSpec(stiffness = Spring.StiffnessMedium),
                    targetScale = 0.94f
                ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
        }

        val modalPredictivePopTransition: AnimatedContentTransitionScope<Scene<*>>.(Int) -> ContentTransform = {
            (scaleIn(animationSpec = PredictivePopFloatSpec, initialScale = 0.96f) +
                fadeIn(animationSpec = PredictivePopFloatSpec)) togetherWith
                (slideOutVertically(
                    animationSpec = PredictivePopIntOffsetSpec,
                    targetOffsetY = { (it * 0.12f).toInt() }
                ) + scaleOut(animationSpec = PredictivePopFloatSpec, targetScale = 0.94f) +
                    fadeOut(animationSpec = PredictivePopFloatSpec))
        }

        val mediaViewerFadeTransition: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform = {
            fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                fadeOut(animationSpec = motionScheme.fastEffectsSpec())
        }

        val mediaViewerPredictivePopTransition: AnimatedContentTransitionScope<Scene<*>>.(Int) -> ContentTransform = {
            fadeIn(animationSpec = PredictivePopFloatSpec) togetherWith
                fadeOut(animationSpec = PredictivePopFloatSpec)
        }

        val modalMetadata = modalNavMetadata(
            enterTransition = modalEnterTransition,
            popTransition = modalPopTransition,
            predictivePopTransition = modalPredictivePopTransition,
        )
        val mediaViewerMetadata = modalNavMetadata(
            enterTransition = mediaViewerFadeTransition,
            popTransition = mediaViewerFadeTransition,
            predictivePopTransition = mediaViewerPredictivePopTransition,
        )

        entryProvider<NavKey> {
            entry<MedQBRoutes.DatabaseSelection> {
                DatabaseSelectionEntry(
                    graph = graph,
                    workflow = workflow,
                    navigator = navigator,
                )
            }

            entry<MedQBRoutes.Filter> {
                FilterEntry(
                    graph = graph,
                    workflow = workflow,
                    navigator = navigator,
                )
            }

            entry<MedQBRoutes.Quiz> { route ->
                QuizEntry(
                    route = route,
                    graph = graph,
                    navigator = navigator,
                    onOpenMedia = onOpenMedia,
                    onOpenHtml = onOpenHtml,
                )
            }

            entry<MedQBRoutes.Settings>(metadata = modalMetadata) {
                SettingsEntry(
                    graph = graph,
                    navigator = navigator,
                )
            }

            entry<MedQBRoutes.MediaViewer>(metadata = mediaViewerMetadata) { key ->
                MediaViewerEntry(
                    key = key,
                    graph = graph,
                    navigator = navigator,
                    onOpenMedia = onOpenMedia,
                    onOpenHtml = onOpenHtml,
                )
            }

            entry<MedQBRoutes.HtmlViewer>(metadata = modalMetadata) { key ->
                HtmlViewerEntry(
                    key = key,
                    graph = graph,
                    navigator = navigator,
                    onOpenMedia = onOpenMedia,
                    onOpenHtml = onOpenHtml,
                )
            }
        }
    }
}
