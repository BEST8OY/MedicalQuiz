package com.medqb.app.shared.ui.richtext.styling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import kotlin.math.abs

internal fun HighlightColor.toSwatchColor(isDarkTheme: Boolean): Color = if (isDarkTheme) {
    when (this) {
        HighlightColor.YELLOW -> Color(0xFFEAB308)
        HighlightColor.GREEN -> Color(0xFF22C55E)
        HighlightColor.BLUE -> Color(0xFF3B82F6)
        HighlightColor.PINK -> Color(0xFFEC4899)
        HighlightColor.ORANGE -> Color(0xFFF97316)
    }
} else {
    when (this) {
        HighlightColor.YELLOW -> Color(0xFFFACC15)
        HighlightColor.GREEN -> Color(0xFF4ADE80)
        HighlightColor.BLUE -> Color(0xFF60A5FA)
        HighlightColor.PINK -> Color(0xFFF472B6)
        HighlightColor.ORANGE -> Color(0xFFFB923C)
    }
}

internal fun HighlightColor.toHighlightBackgroundColor(isDarkTheme: Boolean): Color = if (isDarkTheme) {
    when (this) {
        HighlightColor.YELLOW -> Color(0xFFCA8A04).copy(alpha = 0.34f)
        HighlightColor.GREEN -> Color(0xFF15803D).copy(alpha = 0.38f)
        HighlightColor.BLUE -> Color(0xFF1D4ED8).copy(alpha = 0.40f)
        HighlightColor.PINK -> Color(0xFFBE185D).copy(alpha = 0.38f)
        HighlightColor.ORANGE -> Color(0xFFC2410C).copy(alpha = 0.38f)
    }
} else {
    when (this) {
        HighlightColor.YELLOW -> Color(0xFFFDE047).copy(alpha = 0.42f)
        HighlightColor.GREEN -> Color(0xFF4ADE80).copy(alpha = 0.32f)
        HighlightColor.BLUE -> Color(0xFF60A5FA).copy(alpha = 0.28f)
        HighlightColor.PINK -> Color(0xFFF472B6).copy(alpha = 0.28f)
        HighlightColor.ORANGE -> Color(0xFFFB923C).copy(alpha = 0.34f)
    }
}

/**
 * Filters [highlights] to those anchored to [blockKey] and validates/re-anchors their local
 * `startOffset..endOffset` against [blockText].
 *
 * Fast path: returns the highlight unchanged when `startOffset..endOffset` sits in bounds and
 * matches [TextHighlight.highlightedText] (or when `highlightedText` is empty).
 * Fallback: when a parser update shifts character positions inside the block, locates the
 * occurrence of `highlightedText` nearest to `startOffset` (exact match first, then
 * case-insensitive) and re-anchors the range; unresolvable spans are dropped.
 */
internal fun resolveBlockHighlights(
    blockText: String,
    blockKey: String,
    highlights: List<TextHighlight>,
): List<TextHighlight> {
    if (highlights.isEmpty() || blockText.isEmpty()) return emptyList()
    return highlights.mapNotNull { highlight ->
        if (highlight.blockKey != blockKey) return@mapNotNull null

        val quote = highlight.highlightedText
        val start = highlight.startOffset
        val end = highlight.endOffset

        if (quote.isEmpty()) {
            val clampedStart = start.coerceIn(0, blockText.length)
            val clampedEnd = end.coerceIn(clampedStart, blockText.length)
            return@mapNotNull if (clampedStart < clampedEnd) {
                highlight.copy(startOffset = clampedStart, endOffset = clampedEnd)
            } else null
        }

        if (start >= 0 &&
            end <= blockText.length &&
            end - start == quote.length &&
            blockText.regionMatches(start, quote, 0, quote.length)
        ) {
            return@mapNotNull highlight
        }

        val reanchoredStart = findNearestQuoteIndex(blockText, quote, start, ignoreCase = false)
            ?: findNearestQuoteIndex(blockText, quote, start, ignoreCase = true)
            ?: return@mapNotNull null

        highlight.copy(
            startOffset = reanchoredStart,
            endOffset = reanchoredStart + quote.length,
        )
    }
}

private fun findNearestQuoteIndex(
    text: String,
    quote: String,
    hintOffset: Int,
    ignoreCase: Boolean,
): Int? {
    var index = text.indexOf(quote, startIndex = 0, ignoreCase = ignoreCase)
    if (index < 0) return null
    var bestIndex = index
    var bestDistance = abs(index - hintOffset)
    while (true) {
        index = text.indexOf(quote, startIndex = index + 1, ignoreCase = ignoreCase)
        if (index < 0) break
        val distance = abs(index - hintOffset)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}

internal fun applyHighlightsToText(
    text: AnnotatedString,
    highlights: List<TextHighlight>,
    isDarkTheme: Boolean = false,
): AnnotatedString {
    if (highlights.isEmpty()) return text

    return buildAnnotatedString {
        append(text)

        highlights.forEach { highlight ->
            val start = highlight.startOffset.coerceIn(0, text.length)
            val end = highlight.endOffset.coerceIn(start, text.length)
            if (start < end) {
                addStyle(
                    SpanStyle(background = highlight.color.toHighlightBackgroundColor(isDarkTheme)),
                    start,
                    end
                )
            }
        }
    }
}

internal fun applySelectionToText(
    text: AnnotatedString,
    selectionRange: IntRange,
    selectionColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        val start = selectionRange.first.coerceIn(0, text.length)
        val end = (selectionRange.last + 1).coerceIn(start, text.length)
        if (start < end) {
            addStyle(
                SpanStyle(background = selectionColor),
                start,
                end
            )
        }
    }
}
