package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.medqb.app.shared.data.models.TextHighlight

internal fun applyHighlightsToText(
    text: AnnotatedString,
    highlights: List<TextHighlight>
): AnnotatedString {
    if (highlights.isEmpty()) return text

    return buildAnnotatedString {
        append(text)

        highlights.forEach { highlight ->
            val start = highlight.startOffset.coerceIn(0, text.length)
            val end = highlight.endOffset.coerceIn(start, text.length)
            if (start < end) {
                addStyle(
                    SpanStyle(background = highlight.color.toComposeColor().copy(alpha = 0.4f)),
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
