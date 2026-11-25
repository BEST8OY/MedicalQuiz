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
    /** `< 600.dp` — Phone portrait, narrow split-screen. */
    Compact,

    /** `600.dp .. 839.dp` — Tablet portrait, unfolded foldable, smaller phone landscape. */
    Medium,

    /** `840.dp .. 1199.dp` — Tablet landscape, desktop window, large phone landscape. */
    Expanded,

    /** `>= 1200.dp` — Large tablet, desktop window. */
    Large,
}

/**
 * Material 3 adaptive window height classifications.
 */
enum class HeightSizeClass {
    /** `< 480.dp` — Phone landscape, short split-screen. */
    Compact,

    /** `480.dp .. 899.dp` — Tablet landscape, desktop, phone portrait. */
    Medium,

    /** `>= 900.dp` — Tablet portrait, tall phone portrait, tall desktop. */
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
    val isExpandedWidth: Boolean get() = widthClass == WidthSizeClass.Expanded || widthClass == WidthSizeClass.Large
    val isLargeWidth: Boolean get() = widthClass == WidthSizeClass.Large
    val isCompactHeight: Boolean get() = heightClass == HeightSizeClass.Compact

    /** Two-pane layout is enabled at `>= 840.dp` width regardless of height (Quiz, FilterHub). */
    val useTwoPaneLayout: Boolean get() = isExpandedWidth

    /** Modal bottom sheets promote to dialogs only when both wide (`>= 840.dp`) and tall (`>= 480.dp`). */
    val useDialogForModalSheets: Boolean get() = isExpandedWidth && !isCompactHeight

    val horizontalPadding: Dp
        get() = when (widthClass) {
            WidthSizeClass.Compact -> 16.dp
            WidthSizeClass.Medium -> 24.dp
            WidthSizeClass.Expanded, WidthSizeClass.Large -> 32.dp
        }

    val dialogWidthFraction: Float
        get() = when (widthClass) {
            WidthSizeClass.Compact -> 0.92f
            WidthSizeClass.Medium -> 0.72f
            WidthSizeClass.Expanded, WidthSizeClass.Large -> 0.56f
        }

    companion object {
        /**
         * Derives [AdaptiveLayoutInfo] from the available viewport [width] and [height].
         */
        fun fromDimensions(width: Dp, height: Dp): AdaptiveLayoutInfo = AdaptiveLayoutInfo(
            windowWidth = width,
            windowHeight = height,
            widthClass = when {
                width >= 1200.dp -> WidthSizeClass.Large
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
