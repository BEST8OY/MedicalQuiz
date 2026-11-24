package com.medqb.app.shared.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.medqb.app.shared.ui.theme.exitSpatialSpec as themeExitSpatialSpec

/**
 * Shared non-bouncing spatial specification for spatial dismissals (slideOut, scaleOut).
 * Delegates to [com.medqb.app.shared.ui.theme.exitSpatialSpec].
 */
fun <T> exitSpatialSpec(): FiniteAnimationSpec<T> = themeExitSpatialSpec()

/**
 * Helper to construct NavDisplay transition metadata for modal scenes.
 */
fun modalNavMetadata(
    enterTransition: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform,
    popTransition: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform,
): Map<String, Any> = metadata {
    put(NavDisplay.TransitionKey) { enterTransition() }
    put(NavDisplay.PopTransitionKey) { popTransition() }
    put(NavDisplay.PredictivePopTransitionKey) { popTransition() }
}
