package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.text.AnnotatedString

internal fun handleAnnotatedTextTap(
    text: AnnotatedString,
    offset: Int,
    onLinkClick: ((String) -> Unit)?,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
): Boolean {
    text.getStringAnnotations("TOOLTIP", offset, offset).firstOrNull()?.let { annotation ->
        val rawTitle = text.text.substring(annotation.start, annotation.end).trim()
        val title = rawTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        onTooltipClick?.invoke(
            RichTextTooltipContent(
                title = title,
                message = annotation.item
            )
        )
        return true
    }

    text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
        if (it.item.isNotBlank()) {
            onLinkClick?.invoke(it.item)
            return true
        }
    }

    return false
}
