package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.medicalquiz.app.shared.data.models.TextHighlight
import com.medicalquiz.app.shared.ui.theme.HighlightColorScheme
import com.medicalquiz.app.shared.ui.theme.toContainerColors

internal fun resolveHighlightBackground(
    highlight: TextHighlight,
    scheme: HighlightColorScheme
): Color {
    val (container, _) = highlight.color.toContainerColors(scheme)
    return container.copy(alpha = 0.65f)
}

internal fun applyHighlightsToText(
    text: AnnotatedString,
    highlights: List<TextHighlight>,
    highlightBackgrounds: Map<Long, Color> = emptyMap()
): AnnotatedString {
    if (highlights.isEmpty()) return text

    return buildAnnotatedString {
        append(text)

        highlights.forEach { highlight ->
            val start = highlight.startOffset.coerceIn(0, text.length)
            val end = highlight.endOffset.coerceIn(start, text.length)
            if (start < end) {
                val bgColor = highlightBackgrounds[highlight.id]
                    ?: Color(0xFFFFF9C4).copy(alpha = 0.4f)
                addStyle(
                    SpanStyle(background = bgColor),
                    start,
                    end
                )
                addStringAnnotation("HIGHLIGHT", highlight.id.toString(), start, end)
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
