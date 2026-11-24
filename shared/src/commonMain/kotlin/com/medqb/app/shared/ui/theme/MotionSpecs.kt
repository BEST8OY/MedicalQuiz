package com.medqb.app.shared.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Shared non-bouncing spatial specification for spatial dismissals (slideOut, scaleOut, shrink).
 * Satisfies the Spatial Exit Non-Bouncing Guarantee in AGENTS.md.
 */
fun <T> exitSpatialSpec(
    stiffness: Float = Spring.StiffnessMediumLow,
    visibilityThreshold: T? = null,
): FiniteAnimationSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = stiffness,
    visibilityThreshold = visibilityThreshold,
)
