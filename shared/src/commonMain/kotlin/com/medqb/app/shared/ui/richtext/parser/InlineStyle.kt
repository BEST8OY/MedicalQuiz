package com.medqb.app.shared.ui.richtext.parser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.medqb.app.shared.ui.richtext.RichTextPalette

/**
 * Represents inline text styling options.
 *
 * @property bold Whether text should be bold
 * @property italic Whether text should be italic
 * @property underline Whether text should be underlined
 * @property monospace Whether to use monospace font
 * @property superscript Whether to render as superscript
 * @property subscript Whether to render as subscript
 * @property link URL for link styling, or null
 * @property highlight Highlight type, or null
 * @property dictionary Whether this is dictionary/definition text
 * @property preserveWhitespace Whether to preserve whitespace (non-breaking)
 * @property smallText Whether to use smaller font size
 * @property textColor Override text color, or null
 * @property tooltip Tooltip text, or null
 */
internal data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val monospace: Boolean = false,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
    val link: String? = null,
    val highlight: InlineHighlight? = null,
    val dictionary: Boolean = false,
    val preserveWhitespace: Boolean = false,
    val smallText: Boolean = false,
    val textColor: Color? = null,
    val tooltip: String? = null
)

/**
 * Types of inline highlighting.
 */
internal enum class InlineHighlight {
    IMPORTANT,
    SELECTED
}

/**
 * Applies CSS class styles to an InlineStyle.
 *
 * @param classes Set of CSS class names
 * @param palette Color palette for styling
 * @param showSelectedHighlight Whether to show selected highlights
 * @return Updated InlineStyle with class styles applied
 */
internal fun InlineStyle.applyClassStyles(
    classes: Set<String>,
    palette: RichTextPalette,
    showSelectedHighlight: Boolean
): InlineStyle {
    if (classes.isEmpty()) return this
    var current = this
    classes.forEach { rawClass ->
        current = when (rawClass.lowercase()) {
            "important" -> current.copy(highlight = InlineHighlight.IMPORTANT, bold = true)
            "wichtig" -> current.copy(bold = true)
            "selected" -> if (showSelectedHighlight) current.copy(highlight = InlineHighlight.SELECTED) else current
            "dictionary" -> current.copy(dictionary = true, underline = true)
            "nowrap" -> current.copy(preserveWhitespace = true)
            "scientific-name" -> current.copy(italic = true)
            "abstract" -> current.copy(smallText = true, textColor = palette.abstractText)
            "metalink" -> current.copy(textColor = palette.linkText, italic = true)
            else -> current
        }
    }
    return current
}

internal fun InlineStyle.toSpanStyle(palette: RichTextPalette): SpanStyle {
    val textColor = when {
        this.textColor != null -> this.textColor
        highlight == InlineHighlight.IMPORTANT -> palette.importantText
        highlight == InlineHighlight.SELECTED -> palette.selectedText
        dictionary -> palette.dictionaryText
        tooltip != null -> palette.dictionaryText
        link != null -> palette.linkText
        else -> null
    }

    val backgroundColor = when (highlight) {
        InlineHighlight.IMPORTANT -> palette.importantBackground
        InlineHighlight.SELECTED -> palette.selectedBackground
        null -> Color.Unspecified
    }

    val needsUnderline = underline || dictionary || tooltip != null

    return SpanStyle(
        fontWeight = if (bold) FontWeight.SemiBold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (needsUnderline) TextDecoration.Underline else null,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        baselineShift = when {
            superscript -> BaselineShift.Superscript
            subscript -> BaselineShift.Subscript
            else -> BaselineShift.None
        },
        background = backgroundColor,
        color = textColor ?: Color.Unspecified,
        fontSize = when {
            superscript || subscript -> 0.75.em
            smallText -> 0.85.em
            else -> TextUnit.Unspecified
        },
    )
}
