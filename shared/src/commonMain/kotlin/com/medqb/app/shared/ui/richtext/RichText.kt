package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.richtext.parser.RichTextParser

@Immutable
data class RichTextHighlightConfig(
    val section: HighlightSection,
    val highlights: List<TextHighlight>,
    val onHighlightAdd: (HighlightSection, blockKey: String, startOffset: Int, endOffset: Int, highlightedText: String, color: HighlightColor) -> Unit,
    val onHighlightRemove: (highlightId: Long) -> Unit,
    val onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    val onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
)

/**
 * Renders HTML content as styled Compose UI elements, with optional text highlighting support.
 */
@Composable
fun RichText(
    html: String,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null,
    palette: RichTextPalette = rememberRichTextPalette(),
    highlightConfig: RichTextHighlightConfig? = null,
) {
    val blocks = rememberRichTextBlocks(html, palette, showSelectedHighlight)
    RichTextInternal(
        blocks = blocks,
        tooltipResetKey = html,
        modifier = modifier,
        showSelectedHighlight = showSelectedHighlight,
        onLinkClick = onLinkClick,
        onMediaClick = onMediaClick,
        onTooltipClick = onTooltipClick,
        highlightConfig = highlightConfig,
    )
}

@Composable
fun RichText(
    blocks: List<RichTextBlock>,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    RichTextInternal(
        blocks = blocks,
        tooltipResetKey = blocks,
        modifier = modifier,
        showSelectedHighlight = showSelectedHighlight,
        onLinkClick = onLinkClick,
        onMediaClick = onMediaClick,
        onTooltipClick = onTooltipClick,
        highlightConfig = highlightConfig,
    )
}

@Composable
private fun RichTextInternal(
    blocks: List<RichTextBlock>,
    tooltipResetKey: Any?,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    if (blocks.isEmpty()) return
    val sourceTag = if (highlightConfig != null) "HighlightableRichText" else "RichText"
    val resolvedLinkHandler = rememberResolvedLinkHandler(onLinkClick, sourceTag = sourceTag)
    val resolvedMediaHandler = rememberResolvedMediaHandler(onMediaClick)
    val tooltipSupport = rememberRichTextTooltipSupport(
        resetKey = tooltipResetKey,
        onTooltipClick = onTooltipClick
    )
    val renderBlocks: @Composable (Modifier) -> Unit = { columnModifier ->
        Column(
            modifier = columnModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            blocks.forEachIndexed { blockIndex, block ->
                key(blockIndex) {
                    RichTextBlockRenderer(
                        block = block,
                        blockIndex = blockIndex,
                        showSelectedHighlight = showSelectedHighlight,
                        onLinkClick = resolvedLinkHandler,
                        onMediaClick = resolvedMediaHandler,
                        onTooltipClick = tooltipSupport.onTooltipClick,
                        highlightConfig = highlightConfig,
                    )
                }
            }
        }
    }

    if (highlightConfig != null && LocalRichTextHighlightHostState.current == null) {
        val fallbackHostState = rememberRichTextHighlightHostState()
        RichTextHighlightHost(
            state = fallbackHostState,
            modifier = modifier,
        ) {
            renderBlocks(Modifier)
        }
    } else {
        renderBlocks(modifier)
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
    return remember(rawHtml, palette, showSelectedHighlight) {
        val sanitizedHtml = rawHtml.trim()
        if (sanitizedHtml.isEmpty()) emptyList()
        else RichTextParser.parse(sanitizedHtml, palette, showSelectedHighlight)
    }
}

@Composable
internal fun rememberRichTextPalette(
    dictionaryText: Color = MaterialTheme.colorScheme.primary,
): RichTextPalette {
    val colors = MaterialTheme.colorScheme
    return remember(colors, dictionaryText) {
        RichTextPalette(
            importantBackground = colors.tertiaryContainer,
            importantText = colors.onTertiaryContainer,
            selectedBackground = colors.primaryContainer,
            selectedText = colors.onPrimaryContainer,
            linkText = colors.primary,
            dictionaryText = dictionaryText,
            abstractText = colors.onSurfaceVariant
        )
    }
}
