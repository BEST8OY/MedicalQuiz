package com.medqb.app.shared.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Official Material Design 3 Design System Dimension Tokens.
 *
 * Single source of truth for spacing, icon sizes, elevation, layout constraints,
 * and component metrics across all platforms (Android & Desktop).
 *
 * @see <a href="https://m3.material.io/styles/spacing/overview">M3 Spacing System</a>
 * @see <a href="https://m3.material.io/styles/elevation/tokens">M3 Elevation System</a>
 */

/**
 * Official Material Design 3 Spacing Tokens scale.
 * Built on an 8dp base grid with 4dp nested units per M3 guidelines.
 */
object Spacing {
    /** M3 None: 0dp */
    val None: Dp = 0.dp

    /** Micro gap: ultra-tight proximity, inline stats (2dp) */
    val Micro: Dp = 2.dp

    /** M3 Extra Small gap: tight text pairs, internal chip gaps (4dp) */
    val ExtraSmall: Dp = 4.dp

    /** Sub-small gap: tight icon-to-label proximity (6dp) */
    val SubSmall: Dp = 6.dp

    /** M3 Small gap: list item spacing, divider proximity (8dp) */
    val Small: Dp = 8.dp

    /** M3 Medium Small gap: card inner rows, compact list item spacing (12dp) */
    val MediumSmall: Dp = 12.dp

    /** M3 Medium gap: standard card padding, component insets, toolbar items (16dp) */
    val Medium: Dp = 16.dp

    /** M3 Medium Large gap: section sub-spacing, button height padding (20dp) */
    val MediumLarge: Dp = 20.dp

    /** M3 Large gap: screen margins, major component separation (24dp) */
    val Large: Dp = 24.dp

    /** M3 Extra Large gap: major structural content blocks (32dp) */
    val ExtraLarge: Dp = 32.dp

    /** M3 Extra Extra Large gap: hero spacing, generous section margins (48dp) */
    val ExtraExtraLarge: Dp = 48.dp

    /** M3 Hero / Maximum section gap (64dp) */
    val Hero: Dp = 64.dp
}

/**
 * Container inset tokens aligned with M3 layout margins.
 */
object Inset {
    /** Small container inset (12dp) */
    val Small: Dp get() = Spacing.MediumSmall

    /** Medium container inset (16dp) */
    val Medium: Dp get() = Spacing.Medium

    /** Large container inset (24dp) */
    val Large: Dp get() = Spacing.Large

    /** Extra-large container inset (40dp) */
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
 * Material Design 3 Tonal & Shadow Elevation Tokens.
 */
object Elevation {
    /** Level 0: Flat surfaces (0dp) */
    val Level0: Dp = 0.dp

    /** Level 1: Cards, small popups, low elevation (1dp) */
    val Level1: Dp = 1.dp

    /** Level 2: Floating cards, menus (3dp) */
    val Level2: Dp = 3.dp

    /** Level 3: Modals, nav bars, search bars (6dp) */
    val Level3: Dp = 6.dp

    /** Level 4: Floating action buttons, dialogs (8dp) */
    val Level4: Dp = 8.dp

    /** Level 5: High elevation modals (12dp) */
    val Level5: Dp = 12.dp
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
 * Corner radius scale tokens complementing Compose Material 3 Shapes.
 */
object CornerRadius {
    val None: Dp = 0.dp
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val ExtraLarge: Dp = 28.dp
    val Full: Dp = 9999.dp
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

/**
 * Responsive dialog layout tokens.
 */
object DialogLayout {
    /** Fraction of screen width for compact viewports (phones) */
    const val CompactWidthFraction: Float = 0.92f

    /** Fraction of screen width for expanded viewports (tablets/desktop) */
    const val ExpandedWidthFraction: Float = 0.65f

    /** Safety margin subtracted from screen height for dialog container max height */
    val MaxHeightInset: Dp get() = Spacing.ExtraLarge

    /** Minimum structural container height for dialog shell */
    val MinContainerHeight: Dp = 200.dp

    /** Viewport height threshold for compact landscape dialog adjustments */
    val CompactHeightThreshold: Dp get() = ScreenLayout.CompactHeightBreakpoint

    /** Compact vertical padding for list items in landscape mode */
    val CompactItemPadding: Dp get() = Spacing.ExtraSmall

    /** Compact padding for input fields in landscape mode */
    val CompactInputPadding: Dp get() = Spacing.Micro
}

/**
 * Text selection highlight toolbar tokens.
 */
object HighlightToolbar {
    /** Diameter for highlight color swatches (36dp - 4dp grid step) */
    val ColorChipSize: Dp get() = IconSize.ExtraLarge

    /** Height of vertical divider separating controls (32dp) */
    val DividerHeight: Dp = 32.dp

    /** Tonal elevation for floating toolbar surface */
    val TonalElevation: Dp get() = Elevation.Level3

    /** Shadow elevation for floating toolbar surface */
    val ShadowElevation: Dp get() = Elevation.Level4
}
