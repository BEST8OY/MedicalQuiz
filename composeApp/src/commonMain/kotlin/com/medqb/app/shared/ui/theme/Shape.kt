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

/**
 * Component-specific shape values for consistent UI
 */
object AppShapes {
    /** Cards and elevated surfaces */
    val CardShape = RoundedCornerShape(16.dp)

    /** Buttons and interactive elements */
    val ButtonShape = RoundedCornerShape(12.dp)

    /** Chips and tags */
    val ChipShape = RoundedCornerShape(28.dp)

    /** Bottom sheets and dialogs */
    val BottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Text fields and input surfaces */
    val InputShape = RoundedCornerShape(8.dp)

    /** Small indicators and badges */
    val IndicatorShape = RoundedCornerShape(4.dp)

    /** Toolbar pill selection indicator */
    val ToolbarPillShape = RoundedCornerShape(24.dp)

    /** Fully rounded for FABs and avatars */
    val FullyRounded = RoundedCornerShape(50)
}
