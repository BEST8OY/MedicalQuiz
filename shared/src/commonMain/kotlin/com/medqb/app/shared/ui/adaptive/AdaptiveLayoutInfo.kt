package com.medqb.app.shared.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 adaptive window width classifications.
 */
enum class WidthSizeClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * Material 3 adaptive window height classifications.
 */
enum class HeightSizeClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * Single source of truth for window dimensions, Material 3 size classes,
 * and derived adaptive layout tokens across MedQB.
 */
@Immutable
data class AdaptiveLayoutInfo(
    val windowWidth: Dp = 412.dp,
    val windowHeight: Dp = 915.dp,
    val widthClass: WidthSizeClass = WidthSizeClass.Compact,
    val heightClass: HeightSizeClass = HeightSizeClass.Expanded,
) {
    val isMediumOrWider: Boolean get() = widthClass != WidthSizeClass.Compact
    val isExpandedWidth: Boolean get() = widthClass == WidthSizeClass.Expanded
    val isCompactHeight: Boolean get() = heightClass == HeightSizeClass.Compact

    val horizontalPadding: Dp
        get() = when (widthClass) {
            WidthSizeClass.Compact -> 16.dp
            WidthSizeClass.Medium -> 24.dp
            WidthSizeClass.Expanded -> 32.dp
        }

    val dialogWidthFraction: Float
        get() = when (widthClass) {
            WidthSizeClass.Compact -> 0.92f
            WidthSizeClass.Medium -> 0.72f
            WidthSizeClass.Expanded -> 0.56f
        }

    companion object {
        /**
         * Derives [AdaptiveLayoutInfo] from the available viewport [width] and [height].
         */
        fun fromDimensions(width: Dp, height: Dp): AdaptiveLayoutInfo = AdaptiveLayoutInfo(
            windowWidth = width,
            windowHeight = height,
            widthClass = when {
                width >= 840.dp -> WidthSizeClass.Expanded
                width >= 600.dp -> WidthSizeClass.Medium
                else -> WidthSizeClass.Compact
            },
            heightClass = when {
                height >= 900.dp -> HeightSizeClass.Expanded
                height >= 480.dp -> HeightSizeClass.Medium
                else -> HeightSizeClass.Compact
            },
        )
    }
}

val LocalAdaptiveLayoutInfo = compositionLocalOf { AdaptiveLayoutInfo() }

/**
 * Measures the root viewport once and provides [LocalAdaptiveLayoutInfo] to all descendants.
 */
@Composable
fun ProvideAdaptiveLayoutInfo(
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val info = remember(maxWidth, maxHeight) {
            AdaptiveLayoutInfo.fromDimensions(maxWidth, maxHeight)
        }
        CompositionLocalProvider(LocalAdaptiveLayoutInfo provides info) {
            content()
        }
    }
}
