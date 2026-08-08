package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.text.AnnotatedString

internal fun handleAnnotatedTextTap(
    text: AnnotatedString,
    offset: Int,
    onLinkClick: ((String) -> Unit)?,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
): Boolean {
    // Use offset + 1 so the query covers [offset, offset+1), which correctly
    // matches the first character of a span at annotation.start == offset.
    val tooltipAnnotation = text.getStringAnnotations("TOOLTIP", offset, offset + 1).firstOrNull()
    if (tooltipAnnotation != null && onTooltipClick != null) {
        val rawTitle = text.text.substring(tooltipAnnotation.start, tooltipAnnotation.end).trim()
        val title = rawTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        onTooltipClick(
            RichTextTooltipContent(
                title = title,
                message = tooltipAnnotation.item
            )
        )
        return true
    }

    val urlAnnotation = text.getStringAnnotations("URL", offset, offset + 1).firstOrNull()
    if (urlAnnotation != null && urlAnnotation.item.isNotBlank() && onLinkClick != null) {
        onLinkClick(urlAnnotation.item)
        return true
    }

    return false
}
