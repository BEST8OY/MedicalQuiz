package com.medqb.app.shared.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay

val PredictivePopFloatSpec: FiniteAnimationSpec<Float> =
    tween(durationMillis = 300, easing = LinearEasing)

val PredictivePopIntOffsetSpec: FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = 300, easing = LinearEasing)

/**
 * Helper to construct NavDisplay transition metadata for modal scenes.
 */
fun modalNavMetadata(
    enterTransition: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform,
    popTransition: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform,
    predictivePopTransition: AnimatedContentTransitionScope<Scene<*>>.(swipeEdge: Int) -> ContentTransform,
): Map<String, Any> = metadata {
    put(NavDisplay.TransitionKey, enterTransition)
    put(NavDisplay.PopTransitionKey, popTransition)
    put(NavDisplay.PredictivePopTransitionKey, predictivePopTransition)
}
