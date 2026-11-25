package com.medqb.app.shared.ui.richtext.interaction

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import com.medqb.app.shared.ui.richtext.RichTextTooltipContent

internal fun isPositionWithinTextBounds(
    layout: TextLayoutResult,
    position: Offset,
): Boolean {
    if (position.y < 0f || position.y > layout.size.height.toFloat()) return false
    val line = layout.getLineForVerticalPosition(position.y)
    if (position.y < layout.getLineTop(line) || position.y > layout.getLineBottom(line)) return false
    val lineLeft = layout.getLineLeft(line)
    val lineRight = layout.getLineRight(line)
    return lineRight > lineLeft && position.x >= lineLeft && position.x <= lineRight
}

internal fun extractTooltipAtOffset(
    text: AnnotatedString,
    offset: Int,
): RichTextTooltipContent? {
    if (text.isEmpty() || offset !in 0 until text.length) return null
    val tooltipAnnotation = text.getStringAnnotations("TOOLTIP", offset, offset + 1).firstOrNull()
        ?: return null
    val rawTitle = text.text.substring(tooltipAnnotation.start, tooltipAnnotation.end).trim()
    val title = rawTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    return RichTextTooltipContent(
        title = title,
        message = tooltipAnnotation.item
    )
}

internal fun extractUrlAtOffset(
    text: AnnotatedString,
    offset: Int,
): String? {
    if (text.isEmpty() || offset !in 0 until text.length) return null
    return text.getStringAnnotations("URL", offset, offset + 1)
        .firstOrNull()
        ?.item
        ?.takeIf { it.isNotBlank() }
}

internal fun handleAnnotatedTextTap(
    text: AnnotatedString,
    offset: Int,
    onLinkClick: ((String) -> Unit)?,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
): Boolean {
    if (text.isEmpty() || offset !in 0 until text.length) return false

    val tooltipContent = extractTooltipAtOffset(text, offset)
    if (tooltipContent != null && onTooltipClick != null) {
        onTooltipClick(tooltipContent)
        return true
    }

    val url = extractUrlAtOffset(text, offset)
    if (url != null && onLinkClick != null) {
        onLinkClick(url)
        return true
    }

    return false
}
