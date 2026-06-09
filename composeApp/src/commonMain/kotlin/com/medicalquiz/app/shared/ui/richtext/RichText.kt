package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.ui.richtext.parser.RichTextParser

/**
 * Renders HTML content as styled Compose UI elements.
 * 
 * This composable parses HTML and displays it using Material Design 3 components,
 * supporting paragraphs, headings, lists, tables, code blocks, images, and more.
 * 
 * @param html The HTML string to render
 * @param modifier Modifier to apply to the root layout
 * @param showSelectedHighlight Whether to highlight elements marked with 'selected' class
 * @param onLinkClick Optional callback when a link is clicked (null uses default URI handler)
 * @param onMediaClick Optional callback when media is clicked
 * @param onTooltipClick Optional callback when tooltip is triggered
 */
@Composable
fun RichText(
    html: String,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null
) {
    val palette = rememberRichTextPalette()
    val blocks = rememberRichTextBlocks(html, palette, showSelectedHighlight)
    RichText(
        blocks = blocks,
        modifier = modifier,
        onLinkClick = onLinkClick,
        onMediaClick = onMediaClick,
        onTooltipClick = onTooltipClick
    )
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun RichText(
    blocks: List<RichTextBlock>,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null
) {
    if (blocks.isEmpty()) return
    val resolvedLinkHandler = rememberResolvedLinkHandler(onLinkClick, sourceTag = "RichText")
    val resolvedMediaHandler = rememberResolvedMediaHandler(onMediaClick)
    val tooltipSupport = rememberRichTextTooltipSupport(
        resetKey = blocks,
        onTooltipClick = onTooltipClick
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        blocks.forEach { block ->
            RichTextBlockRenderer(
                block = block,
                onLinkClick = resolvedLinkHandler,
                onMediaClick = resolvedMediaHandler,
                onTooltipClick = tooltipSupport.onTooltipClick
            )
        }
    }
    RichTextTooltipBottomSheet(
        content = tooltipSupport.tooltipContent,
        onDismissRequest = tooltipSupport.dismissTooltip
    )
}

@Composable
private fun rememberRichTextBlocks(
    rawHtml: String,
    palette: RichTextPalette,
    showSelectedHighlight: Boolean
): List<RichTextBlock> {
    val sanitizedHtml = remember(rawHtml) { rawHtml.trim() }
    return remember(sanitizedHtml, palette, showSelectedHighlight) {
        if (sanitizedHtml.isEmpty()) emptyList()
        else RichTextParser.parse(sanitizedHtml, palette, showSelectedHighlight)
    }
}

@Composable
internal fun rememberRichTextPalette(): RichTextPalette {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        RichTextPalette(
            importantBackground = colors.tertiaryContainer,
            importantText = colors.onTertiaryContainer,
            selectedBackground = colors.primaryContainer,
            selectedText = colors.onPrimaryContainer,
            linkText = colors.primary,
            dictionaryText = colors.secondary,
            abstractText = colors.onSurfaceVariant
        )
    }
}
