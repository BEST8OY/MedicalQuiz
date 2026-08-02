package com.medqb.app.shared.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3-aligned spacing tokens on a 4dp base grid.
 *
 * Values follow Material Design 3 spacing guidance:
 * - Component internal padding: 4, 8, 12, 16, 24dp
 * - Between components: 8, 12, 16, 24dp
 * - Section spacing: 24, 32, 48dp
 * - Layout margins: 16dp (compact), 24dp (medium+)
 */
object Spacing {
    /** Minimal gap: tight text pairs (title + subtitle) */
    val Xxs = 4.dp

    /** Sub-small gap: toolbar elevation, tight icon-to-label proximity */
    val XxsPlus = 6.dp

    /** Small gap: list item spacing in dialogs, divider proximity */
    val Xs = 8.dp

    /** Medium gap: card row content, compact list spacing */
    val Sm = 12.dp

    /** Standard gap: card padding, component insets, toolbar items */
    val Md = 16.dp

    /** Medium-large gap: section sub-spacing, button minHeight */
    val LgSm = 20.dp

    /** Section gap: between major content blocks, screen margins */
    val Lg = 24.dp

    /** Large section gap */
    val Xl = 32.dp

    /** Extra large gap: hero spacing, generous margins */
    val Xxl = 48.dp

    /** Micro gap: stat items, ultra-tight proximity */
    val Micro = 2.dp
}

/**
 * Container inset tokens. Direct aliases to M3 spacing guidance.
 */
object Inset {
    val Sm: Dp get() = Spacing.Sm
    val Md: Dp get() = Spacing.Md
    val Lg: Dp get() = Spacing.Lg
    val Xl: Dp = 40.dp
}

/**
 * Fixed-dimension tokens for icons and icon containers.
 * All values on 4dp grid per M3 guidance.
 */
object ElementSize {
    /** Small icon (checkmarks, inline indicators) */
    val IconSm: Dp = 16.dp

    /** Standard icon (e.g. action button icons, swipe actions) */
    val IconMd: Dp = 20.dp

    /** Large icon (e.g. history icon, checkbox animated width) */
    val IconLg: Dp = 24.dp

    /** Extra-large icon (e.g. rich text toolbar controls) */
    val IconXl: Dp = 36.dp

    /** Icon container: medium (badge, leading content) */
    val IconContainerMd: Dp = 40.dp

    /** Icon container: large (filter cards, toggle cards) */
    val IconContainerLg: Dp = 44.dp

    /** Icon container: extra-large (error state) */
    val IconContainerXl: Dp = 56.dp

    /** Medium-large icon (between IconMd and IconXl) */
    val IconMdLg: Dp = 28.dp
}

/**
 * Border/stroke width tokens.
 */
object Stroke {
    /** Thin stroke: card outlines, table borders */
    val Thin: Dp = 1.dp

    /** Thick stroke: selection highlight borders */
    val Thick: Dp = 2.5.dp
}

/**
 * Layout constraint tokens for widths and heights.
 */
object Layout {
    /** Max width for toolbar and button group */
    val MaxContentWidth: Dp = 320.dp

    /** Minimum width for single-button group */
    val SingleButtonMinWidth: Dp = 176.dp

    /** Minimum touch target size (M3 accessibility minimum) */
    val MinTouchTarget: Dp = 48.dp

    /** Max height for selectable item lists in dialogs */
    val ItemListMaxHeight: Dp = 320.dp

    /** Height for loading indicator areas */
    val LoadingAreaHeight: Dp = 200.dp

    /** Width for dialog panels and media containers */
    val PanelWidth: Dp = 280.dp

    /** Max width for rich text media content */
    val MediaMaxWidth: Dp = 512.dp

    /** Minimum width per table column before horizontal scrolling kicks in */
    val TableMinCellWidth: Dp = 120.dp
}

/**
 * Screen-level layout tokens for breakpoints and clearances.
 */
object ScreenLayout {
    /** Height threshold for compact layout adjustments */
    val CompactHeightBreakpoint: Dp = 400.dp

    /** Width threshold for wide/expanded screen layouts (M3 Expanded class) */
    val WideWidthBreakpoint: Dp = 840.dp

    /** Width threshold for compact single-column layouts */
    val CompactWidthBreakpoint: Dp = 600.dp

    /** Bottom padding for compact screens */
    val BottomPaddingCompact: Dp = 64.dp

    /** Bottom padding for normal screens (accounts for toolbar) */
    val BottomPaddingDefault: Dp = 88.dp

    /** Bottom padding for FAB menu clearance */
    val FabBottomPadding: Dp = 96.dp

    /** Bottom padding with FAB clearance (list padding + FAB) */
    val BottomPaddingWithFab: Dp = 112.dp

    /** Bottom clearance for content above floating elements */
    val BottomClearanceFloating: Dp = 80.dp

    /** Top padding for empty state messages */
    val EmptyStatePadding: Dp = 72.dp
}

/**
 * Responsive dialog layout tokens.
 */
object DialogLayout {
    /** Fraction of screen width for compact viewports */
    const val CompactWidthFraction: Float = 0.92f

    /** Fraction of screen width for expanded viewports (tablets/desktop) */
    const val ExpandedWidthFraction: Float = 0.65f

    /** Safety margin subtracted from screen height for dialog container max height */
    val MaxHeightInset: Dp get() = Spacing.Xl
}
