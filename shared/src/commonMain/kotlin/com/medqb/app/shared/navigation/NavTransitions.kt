package com.medqb.app.shared.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay

/**
 * Shared non-bouncing spatial specification for spatial dismissals (slideOut, scaleOut).
 * Satisfies the Spatial Exit Non-Bouncing Guarantee in AGENTS.md.
 */
fun <T> exitSpatialSpec(): FiniteAnimationSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

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
