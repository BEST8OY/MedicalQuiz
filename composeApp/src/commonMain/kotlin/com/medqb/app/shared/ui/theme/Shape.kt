package com.medqb.app.shared.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape Tokens
 * Extended shape scale for modern, approachable UI
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Shapes = Shapes(
    extraSmall = ShapeDefaults.ExtraSmall,
    small = ShapeDefaults.Small,
    medium = ShapeDefaults.Medium,
    large = ShapeDefaults.Large,
    extraLarge = ShapeDefaults.ExtraLarge,
    largeIncreased = ShapeDefaults.LargeIncreased,
    extraLargeIncreased = ShapeDefaults.ExtraLargeIncreased,
    extraExtraLarge = ShapeDefaults.ExtraExtraLarge,
)
