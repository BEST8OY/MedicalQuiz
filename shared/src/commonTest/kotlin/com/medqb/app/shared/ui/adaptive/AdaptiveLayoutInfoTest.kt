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
        assertEquals(16.dp, compact.horizontalPadding)
        assertEquals(0.92f, compact.dialogWidthFraction)

        val mediumStart = AdaptiveLayoutInfo.fromDimensions(width = 600.dp, height = 800.dp)
        assertEquals(WidthSizeClass.Medium, mediumStart.widthClass)
        assertTrue(mediumStart.isMediumOrWider)
        assertFalse(mediumStart.isExpandedWidth)
        assertEquals(24.dp, mediumStart.horizontalPadding)
        assertEquals(0.72f, mediumStart.dialogWidthFraction)

        val mediumEnd = AdaptiveLayoutInfo.fromDimensions(width = 839.dp, height = 800.dp)
        assertEquals(WidthSizeClass.Medium, mediumEnd.widthClass)

        val expanded = AdaptiveLayoutInfo.fromDimensions(width = 840.dp, height = 800.dp)
        assertEquals(WidthSizeClass.Expanded, expanded.widthClass)
        assertTrue(expanded.isMediumOrWider)
        assertTrue(expanded.isExpandedWidth)
        assertEquals(32.dp, expanded.horizontalPadding)
        assertEquals(0.56f, expanded.dialogWidthFraction)
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
}
