package com.medicalquiz.app.shared.ui.richtext.parser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.medicalquiz.app.shared.ui.richtext.RichTextPalette

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
 * Builder class for constructing InlineStyle instances.
 */
internal class InlineStyleBuilder(initial: InlineStyle = InlineStyle()) {
    var bold: Boolean = initial.bold
    var italic: Boolean = initial.italic
    var underline: Boolean = initial.underline
    var monospace: Boolean = initial.monospace
    var superscript: Boolean = initial.superscript
    var subscript: Boolean = initial.subscript
    var link: String? = initial.link
    var highlight: InlineHighlight? = initial.highlight
    var dictionary: Boolean = initial.dictionary
    var preserveWhitespace: Boolean = initial.preserveWhitespace
    var smallText: Boolean = initial.smallText
    var textColor: Color? = initial.textColor
    var tooltip: String? = initial.tooltip

    fun build(): InlineStyle = InlineStyle(
        bold = bold,
        italic = italic,
        underline = underline,
        monospace = monospace,
        superscript = superscript,
        subscript = subscript,
        link = link,
        highlight = highlight,
        dictionary = dictionary,
        preserveWhitespace = preserveWhitespace,
        smallText = smallText,
        textColor = textColor,
        tooltip = tooltip
    )
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
    val builder = InlineStyleBuilder(this)
    classes.forEach { rawClass ->
        when (rawClass.lowercase()) {
            "important", "wichtig" -> {
                builder.highlight = InlineHighlight.IMPORTANT
                builder.bold = true
            }
            "selected" -> if (showSelectedHighlight) {
                builder.highlight = InlineHighlight.SELECTED
            }
            "dictionary" -> {
                builder.dictionary = true
                builder.underline = true
            }
            "nowrap" -> builder.preserveWhitespace = true
            "scientific-name" -> builder.italic = true
            "abstract" -> {
                builder.smallText = true
                builder.textColor = palette.abstractText
            }
            "metalink" -> {
                builder.textColor = palette.linkText
                builder.italic = true
            }
        }
    }
    return builder.build()
}

/**
 * Appends text with the specified inline style to an AnnotatedString builder.
 *
 * @param text The text to append
 * @param style The inline styling to apply
 * @param palette The color palette for styling
 */
internal fun AnnotatedString.Builder.appendTextWithStyle(
    text: String,
    style: InlineStyle,
    palette: RichTextPalette
) {
    if (text.isEmpty()) return

    val displayText = if (style.preserveWhitespace) text.replace(' ', '\u00A0') else text

    val textColor = when {
        style.textColor != null -> style.textColor
        style.highlight == InlineHighlight.IMPORTANT -> palette.importantText
        style.highlight == InlineHighlight.SELECTED -> palette.selectedText
        style.dictionary -> palette.dictionaryText
        style.tooltip != null -> palette.dictionaryText
        style.link != null -> palette.linkText
        else -> null
    }

    val backgroundColor = when (style.highlight) {
        InlineHighlight.IMPORTANT -> palette.importantBackground
        InlineHighlight.SELECTED -> palette.selectedBackground
        null -> Color.Unspecified
    }

    val needsUnderline = style.underline || style.dictionary || style.tooltip != null

    val spanStyle = SpanStyle(
        fontWeight = if (style.bold) FontWeight.SemiBold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        textDecoration = if (needsUnderline) TextDecoration.Underline else null,
        fontFamily = if (style.monospace) FontFamily.Monospace else FontFamily.Default,
        baselineShift = when {
            style.superscript -> BaselineShift.Superscript
            style.subscript -> BaselineShift.Subscript
            else -> BaselineShift.None
        },
        background = backgroundColor,
        color = textColor ?: Color.Unspecified,
        fontSize = if (style.smallText) 12.sp else TextUnit.Unspecified
    )

    if (style.link != null) {
        pushStringAnnotation(tag = "URL", annotation = style.link)
    }
    if (style.tooltip != null) {
        pushStringAnnotation(tag = "TOOLTIP", annotation = style.tooltip)
    }

    withStyle(spanStyle) {
        append(displayText)
    }

    if (style.tooltip != null) {
        pop()
    }
    if (style.link != null) {
        pop()
    }
}
