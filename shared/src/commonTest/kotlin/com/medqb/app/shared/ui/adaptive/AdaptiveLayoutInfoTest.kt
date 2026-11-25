package com.medqb.app.shared.ui.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveLayoutInfoTest {

    @Test
    fun widthSizeClassBoundariesAreClassifiedCorrectly() {
        val compact = AdaptiveLayoutInfo.fromDimensions(width = 599.dp, height = 800.dp)
        assertEquals(WidthSizeClass.Compact, compact.widthClass)
        assertFalse(compact.isMediumOrWider)
        assertFalse(compact.isExpandedWidth)
        assertFalse(compact.isLargeWidth)
        assertEquals(16.dp, compact.horizontalPadding)
        assertEquals(0.92f, compact.dialogWidthFraction)

        val mediumStart = AdaptiveLayoutInfo.fromDimensions(width = 600.dp, height = 800.dp)
        assertEquals(WidthSizeClass.Medium, mediumStart.widthClass)
        assertTrue(mediumStart.isMediumOrWider)
        assertFalse(mediumStart.isExpandedWidth)
        assertFalse(mediumStart.isLargeWidth)
        assertEquals(24.dp, mediumStart.horizontalPadding)
        assertEquals(0.72f, mediumStart.dialogWidthFraction)

        val mediumEnd = AdaptiveLayoutInfo.fromDimensions(width = 839.dp, height = 800.dp)
        assertEquals(WidthSizeClass.Medium, mediumEnd.widthClass)

        val expandedStart = AdaptiveLayoutInfo.fromDimensions(width = 840.dp, height = 800.dp)
        assertEquals(WidthSizeClass.Expanded, expandedStart.widthClass)
        assertTrue(expandedStart.isMediumOrWider)
        assertTrue(expandedStart.isExpandedWidth)
        assertFalse(expandedStart.isLargeWidth)
        assertEquals(32.dp, expandedStart.horizontalPadding)
        assertEquals(0.56f, expandedStart.dialogWidthFraction)

        val expandedEnd = AdaptiveLayoutInfo.fromDimensions(width = 1199.dp, height = 800.dp)
        assertEquals(WidthSizeClass.Expanded, expandedEnd.widthClass)
        assertTrue(expandedEnd.isExpandedWidth)
        assertFalse(expandedEnd.isLargeWidth)

        val large = AdaptiveLayoutInfo.fromDimensions(width = 1200.dp, height = 800.dp)
        assertEquals(WidthSizeClass.Large, large.widthClass)
        assertTrue(large.isMediumOrWider)
        assertTrue(large.isExpandedWidth)
        assertTrue(large.isLargeWidth)
        assertEquals(32.dp, large.horizontalPadding)
        assertEquals(0.56f, large.dialogWidthFraction)
    }

    @Test
    fun heightSizeClassBoundariesAreClassifiedCorrectly() {
        val compact = AdaptiveLayoutInfo.fromDimensions(width = 800.dp, height = 479.dp)
        assertEquals(HeightSizeClass.Compact, compact.heightClass)
        assertTrue(compact.isCompactHeight)

        val mediumStart = AdaptiveLayoutInfo.fromDimensions(width = 800.dp, height = 480.dp)
        assertEquals(HeightSizeClass.Medium, mediumStart.heightClass)
        assertFalse(mediumStart.isCompactHeight)

        val mediumEnd = AdaptiveLayoutInfo.fromDimensions(width = 800.dp, height = 899.dp)
        assertEquals(HeightSizeClass.Medium, mediumEnd.heightClass)
        assertFalse(mediumEnd.isCompactHeight)

        val expanded = AdaptiveLayoutInfo.fromDimensions(width = 800.dp, height = 900.dp)
        assertEquals(HeightSizeClass.Expanded, expanded.heightClass)
        assertFalse(expanded.isCompactHeight)
    }

    @Test
    fun semanticLayoutDecisionsCombineWidthAndHeightCorrectly() {
        val phonePortrait = AdaptiveLayoutInfo.fromDimensions(width = 412.dp, height = 915.dp)
        assertFalse(phonePortrait.useTwoPaneLayout)
        assertFalse(phonePortrait.useDialogForModalSheets)

        val smallPhoneLandscape = AdaptiveLayoutInfo.fromDimensions(width = 800.dp, height = 390.dp)
        assertFalse(smallPhoneLandscape.useTwoPaneLayout)
        assertFalse(smallPhoneLandscape.useDialogForModalSheets)

        val largePhoneLandscape = AdaptiveLayoutInfo.fromDimensions(width = 900.dp, height = 410.dp)
        assertTrue(largePhoneLandscape.useTwoPaneLayout)
        assertFalse(largePhoneLandscape.useDialogForModalSheets)

        val tabletLandscape = AdaptiveLayoutInfo.fromDimensions(width = 900.dp, height = 600.dp)
        assertTrue(tabletLandscape.useTwoPaneLayout)
        assertTrue(tabletLandscape.useDialogForModalSheets)

        val desktopShortWindow = AdaptiveLayoutInfo.fromDimensions(width = 1280.dp, height = 450.dp)
        assertTrue(desktopShortWindow.useTwoPaneLayout)
        assertFalse(desktopShortWindow.useDialogForModalSheets)

        val desktopLarge = AdaptiveLayoutInfo.fromDimensions(width = 1280.dp, height = 800.dp)
        assertTrue(desktopLarge.useTwoPaneLayout)
        assertTrue(desktopLarge.useDialogForModalSheets)
    }
}
