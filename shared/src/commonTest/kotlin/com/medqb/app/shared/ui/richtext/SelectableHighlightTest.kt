package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.ui.richtext.interaction.findTappedHighlight
import com.medqb.app.shared.ui.richtext.positioning.SelectionToolbarPositionProvider
import com.medqb.app.shared.ui.richtext.styling.resolveBlockHighlights
import com.medqb.app.shared.ui.richtext.styling.toHighlightBackgroundColor
import com.medqb.app.shared.ui.richtext.styling.toSwatchColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectableHighlightTest {

    @Test
    fun testHighlightColorAdaptivePalette() {
        HighlightColor.entries.forEach { color ->
            val lightSwatch = color.toSwatchColor(isDarkTheme = false)
            val darkSwatch = color.toSwatchColor(isDarkTheme = true)
            val lightBg = color.toHighlightBackgroundColor(isDarkTheme = false)
            val darkBg = color.toHighlightBackgroundColor(isDarkTheme = true)
            assertEquals(1f, lightSwatch.alpha)
            assertEquals(1f, darkSwatch.alpha)
            assertTrue(lightBg.alpha in 0.2f..0.5f)
            assertTrue(darkBg.alpha in 0.2f..0.5f)
            assertNotEquals(lightBg, darkBg)
            assertNotEquals(lightSwatch, darkSwatch)
        }
    }

    @Test
    fun findTappedHighlightPrefersSmallestNestedHighlight() {
        val outer = TextHighlight(
            id = 1L,
            dbName = "db",
            questionId = 1L,
            section = HighlightSection.QUESTION,
            startOffset = 0,
            endOffset = 30,
            highlightedText = "012345678901234567890123456789",
            color = HighlightColor.YELLOW,
            createdAt = 1L
        )
        val inner = outer.copy(
            id = 2L,
            startOffset = 10,
            endOffset = 15,
            highlightedText = "01234",
            color = HighlightColor.BLUE
        )
        assertEquals(inner, findTappedHighlight(listOf(outer, inner), tappedOffset = 12, textLength = 40))
        assertEquals(outer, findTappedHighlight(listOf(outer, inner), tappedOffset = 3, textLength = 40))
        assertNull(findTappedHighlight(listOf(outer, inner), tappedOffset = 35, textLength = 40))
    }

    @Test
    fun selectionToolbarPositionProviderPlacesAboveShortParagraphInWindowSpace() {
        val provider = SelectionToolbarPositionProvider(
            selectionRectInAnchor = Rect(left = 40f, top = 0f, right = 180f, bottom = 24f),
            edgeMarginPx = 16,
            topSafeMarginPx = 12,
            bottomSafeMarginPx = 12,
            aboveGapPx = 10,
            belowGapPx = 36,
        )
        // Single-line 24px paragraph located at y = 400 in window
        val pos = provider.calculatePosition(
            anchorBounds = IntRect(left = 20, top = 400, right = 340, bottom = 424),
            windowSize = IntSize(width = 360, height = 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(width = 250, height = 100),
        )
        // Should sit cleanly above: 400 - 10 - 100 = 290 (NOT covering 400..424!)
        assertEquals(290, pos.y)
        assertTrue(pos.y + 100 <= 400)

        // When near top of window (anchor top = 40), should flip below selection
        val flipped = provider.calculatePosition(
            anchorBounds = IntRect(left = 20, top = 40, right = 340, bottom = 64),
            windowSize = IntSize(width = 360, height = 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(width = 250, height = 100),
        )
        // Should sit below: 40 + 24 + 36 = 100
        assertEquals(100, flipped.y)
    }

    @Test
    fun resolveBlockHighlightsFiltersByBlockKeyAndReanchorsShiftedQuotes() {
        val blockText = "Serum sodium is low; severe sodium depletion follows"
        val exact = TextHighlight(
            id = 1L,
            dbName = "db",
            questionId = 1L,
            section = HighlightSection.QUESTION,
            blockKey = "p:0",
            startOffset = 0,
            endOffset = 5,
            highlightedText = "Serum",
            color = HighlightColor.YELLOW,
            createdAt = 1L,
        )
        // Suppose parser shifts characters so stored offset is 25 while "sodium" occurs at 6 and 28;
        // nearest match to hint=25 must pick the second occurrence at 28..34!
        val shiftedNearestSecond = exact.copy(
            id = 2L,
            startOffset = 25,
            endOffset = 31,
            highlightedText = "sodium",
        )
        val otherBlock = exact.copy(
            id = 3L,
            blockKey = "p:1",
            highlightedText = "sodium",
        )
        val missingQuote = exact.copy(
            id = 4L,
            startOffset = 0,
            endOffset = 10,
            highlightedText = "potassium",
        )

        val resolved = resolveBlockHighlights(
            blockText = blockText,
            blockKey = "p:0",
            highlights = listOf(exact, shiftedNearestSecond, otherBlock, missingQuote),
        )

        assertEquals(2, resolved.size)
        assertEquals(0, resolved[0].startOffset)
        assertEquals(5, resolved[0].endOffset)
        assertEquals(28, resolved[1].startOffset)
        assertEquals(34, resolved[1].endOffset)
        assertEquals("sodium", blockText.substring(resolved[1].startOffset, resolved[1].endOffset))
    }

    @Test
    fun resolveBlockHighlightsFallsBackCaseInsensitivelyAndHandlesEmptyQuote() {
        val blockText = "Hyponatremia workup"
        val caseMismatch = TextHighlight(
            id = 1L,
            dbName = "db",
            questionId = 1L,
            section = HighlightSection.QUESTION,
            blockKey = "h:0",
            startOffset = 3,
            endOffset = 15,
            highlightedText = "HYPONATREMIA",
            color = HighlightColor.BLUE,
            createdAt = 1L,
        )
        val emptyQuoteClamped = caseMismatch.copy(
            id = 2L,
            startOffset = 13,
            endOffset = 50,
            highlightedText = "",
        )

        val resolved = resolveBlockHighlights(
            blockText = blockText,
            blockKey = "h:0",
            highlights = listOf(caseMismatch, emptyQuoteClamped),
        )

        assertEquals(2, resolved.size)
        assertEquals(0, resolved[0].startOffset)
        assertEquals(12, resolved[0].endOffset)
        assertEquals(13, resolved[1].startOffset)
        assertEquals(blockText.length, resolved[1].endOffset)
    }
}
