package com.medqb.app.shared.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Official Material Design 3 Spacing System Tokens.
 *
 * Single source of truth for spacing, layout insets, and component metrics
 * built on a linear 8dp baseline scale (space100 = 8dp) per official M3 guidelines.
 *
 * @see <a href="https://m3.material.io/styles/spacing/tokens">M3 Spacing Tokens</a>
 * @see <a href="https://m3.material.io/styles/spacing/applying-spacing">Applying Spacing</a>
 * @see <a href="https://m3.material.io/styles/spacing/overview">M3 Spacing Overview</a>
 */

/**
 * Official Material Design 3 Spacing Tokens scale.
 * Built on an 8dp base grid with nested fractional units (space25..space800).
 */
object Spacing {
    /** M3 None: 0dp (space0) */
    val None: Dp = 0.dp

    /** Micro gap: ultra-tight proximity, inline status dots (2dp - space25, 0.25x) */
    val Micro: Dp = 2.dp

    /** M3 Extra Small gap: tight text pairs, internal chip gaps (4dp - space50, 0.5x) */
    val ExtraSmall: Dp = 4.dp

    /** Sub-small gap: tight icon-to-label proximity (6dp - space75, 0.75x) */
    val SubSmall: Dp = 6.dp

    /** M3 Small gap: list item spacing, divider proximity (8dp - space100, 1.0x Base Unit) */
    val Small: Dp = 8.dp

    /** M3 Medium Small gap: card inner rows, compact list item spacing (12dp - space150, 1.5x) */
    val MediumSmall: Dp = 12.dp

    /** M3 Medium gap: standard card padding, component insets, toolbar items (16dp - space200, 2.0x) */
    val Medium: Dp = 16.dp

    /** M3 Medium Large gap: section sub-spacing, button height padding (20dp - space250, 2.5x) */
    val MediumLarge: Dp = 20.dp

    /** M3 Large gap: screen margins, major component separation (24dp - space300, 3.0x) */
    val Large: Dp = 24.dp

    /** M3 Extra Large gap: major structural content blocks (32dp - space400, 4.0x) */
    val ExtraLarge: Dp = 32.dp

    /** M3 Extra Extra Large gap: hero spacing, generous section margins (48dp - space600, 6.0x) */
    val ExtraExtraLarge: Dp = 48.dp

    /** M3 Hero / Maximum section gap (64dp - space800, 8.0x) */
    val Hero: Dp = 64.dp
}

/**
 * Container inset tokens aligned with M3 layout margins and structural padding.
 */
object Inset {
    /** Small container inset (12dp - space150) */
    val Small: Dp get() = Spacing.MediumSmall

    /** Medium container inset (16dp - space200) */
    val Medium: Dp get() = Spacing.Medium

    /** Large container inset (24dp - space300) */
    val Large: Dp get() = Spacing.Large

    /** Extra-large container inset (40dp - space500) */
    val ExtraLarge: Dp = 40.dp
}

/**
 * Standardized Icon Size Tokens on M3 4dp grid.
 */
object IconSize {
    /** Small icon (checkmarks, inline indicators) (16dp) */
    val Small: Dp = 16.dp

    /** Standard icon (action button icons, swipe actions) (20dp) */
    val Medium: Dp = 20.dp

    /** Large icon (history icon, checkbox animated width) (24dp) */
    val Large: Dp = 24.dp

    /** Medium-large icon (between Medium and ExtraLarge) (28dp) */
    val MediumLarge: Dp = 28.dp

    /** Extra-large icon (rich text toolbar controls, swatches) (36dp) */
    val ExtraLarge: Dp = 36.dp
}

/**
 * Standardized Container Size Tokens for icon buttons, avatars, badges.
 */
object ContainerSize {
    /** Container medium (badge, leading content) (40dp) */
    val Medium: Dp = 40.dp

    /** Container large (filter cards, toggle cards) (44dp) */
    val Large: Dp = 44.dp

    /** Container extra-large (error state, hero graphics) (56dp) */
    val ExtraLarge: Dp = 56.dp
}

/**
 * Border and stroke width tokens.
 */
object Stroke {
    /** Thin stroke: card outlines, table borders (1dp) */
    val Thin: Dp = 1.dp

    /** Medium stroke: focused state outlines (1.5dp) */
    val Medium: Dp = 1.5.dp

    /** Thick stroke: selection highlight borders (2.5dp) */
    val Thick: Dp = 2.5.dp
}

/**
 * Layout constraint tokens for component widths, heights, and touch targets.
 */
object Layout {
    /** Max width for toolbar and button group */
    val MaxContentWidth: Dp = 320.dp

    /** Minimum width for single-button group */
    val SingleButtonMinWidth: Dp = 176.dp

    /** Minimum touch target size (M3 accessibility minimum) (48dp) */
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
 * Screen-level layout tokens for responsive breakpoints and clearances.
 */
object ScreenLayout {
    /** Height threshold for compact layout adjustments (400dp) */
    val CompactHeightBreakpoint: Dp = 400.dp

    /** Width threshold for wide/expanded screen layouts (M3 Expanded class) (840dp) */
    val WideWidthBreakpoint: Dp = 840.dp

    /** Width threshold for compact single-column layouts (600dp) */
    val CompactWidthBreakpoint: Dp = 600.dp

    /** Bottom padding for compact screens (64dp) */
    val BottomPaddingCompact: Dp = 64.dp

    /** Bottom padding for normal screens (accounts for toolbar) (88dp) */
    val BottomPaddingDefault: Dp = 88.dp

    /** Bottom padding for FAB menu clearance (96dp) */
    val FabBottomPadding: Dp = 96.dp

    /** Bottom padding with FAB clearance (list padding + FAB) (112dp) */
    val BottomPaddingWithFab: Dp = 112.dp

    /** Bottom clearance for content above floating elements (80dp) */
    val BottomClearanceFloating: Dp = 80.dp

    /** Top padding for empty state messages (72dp) */
    val EmptyStatePadding: Dp = 72.dp
}
