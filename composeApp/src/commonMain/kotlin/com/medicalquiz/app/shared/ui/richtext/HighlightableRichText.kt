package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.TextHighlightsRepository
import com.medicalquiz.app.shared.data.models.HighlightColor
import com.medicalquiz.app.shared.data.models.HighlightSection
import com.medicalquiz.app.shared.data.models.TextHighlight
import com.medicalquiz.app.shared.ui.richtext.parser.RichTextParser
import androidx.compose.material3.Text as MaterialText

/**
 * RichText composable with text highlighting support.
 * 
 * This extends the base RichText with the ability to:
 * - Long-press to select text
 * - Drag to adjust selection
 * - Tap color to create highlight
 * - Tap existing highlight to edit/delete
 * 
 * @param html The HTML string to render
 * @param section Which section this is (QUESTION or EXPLANATION)
 * @param highlightsRepository Repository for managing text highlights
 * @param modifier Modifier to apply to the root layout
 * @param showSelectedHighlight Whether to show 'selected' class highlights
 * @param onLinkClick Optional callback when a link is clicked
 * @param onMediaClick Optional callback when media is clicked
 */
@Composable
fun HighlightableRichText(
    html: String,
    section: HighlightSection,
    highlightsRepository: TextHighlightsRepository?,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null
) {
    val palette = rememberRichTextPalette()
    val blocks = remember(html, palette, showSelectedHighlight) {
        if (html.trim().isEmpty()) emptyList()
        else RichTextParser.parse(html.trim(), palette, showSelectedHighlight)
    }
    
    // Get highlights for this section
    val highlights by when (section) {
        HighlightSection.QUESTION -> highlightsRepository?.questionHighlights?.collectAsState()
            ?: remember { mutableStateOf(emptyList()) }
        HighlightSection.EXPLANATION -> highlightsRepository?.explanationHighlights?.collectAsState()
            ?: remember { mutableStateOf(emptyList()) }
    }
    
    val resolvedLinkHandler = rememberLinkHandler(onLinkClick)
    val resolvedMediaHandler = rememberMediaHandler(onMediaClick)
    var tooltipMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(blocks) { tooltipMessage = null }
    val resolvedTooltipHandler = remember(onTooltipClick) {
        onTooltipClick ?: { message -> tooltipMessage = message }
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Track cumulative offset across blocks for proper highlight mapping
        var cumulativeOffset = 0
        
        blocks.forEach { block ->
            HighlightableBlockRenderer(
                block = block,
                section = section,
                highlights = highlights,
                baseOffset = cumulativeOffset,
                onHighlightAdd = { start, end, text, color ->
                    highlightsRepository?.addHighlight(section, start, end, text, color)
                },
                onHighlightRemove = { id ->
                    highlightsRepository?.removeHighlight(id)
                },
                onHighlightColorChange = { id, color ->
                    highlightsRepository?.updateHighlightColor(id, color)
                },
                onLinkClick = resolvedLinkHandler,
                onMediaClick = resolvedMediaHandler,
                onTooltipClick = resolvedTooltipHandler
            )
            
            // Update cumulative offset based on block content
            cumulativeOffset += getBlockTextLength(block) + 1 // +1 for block separator
        }
    }
    
    tooltipMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { tooltipMessage = null },
            confirmButton = {
                TextButton(onClick = { tooltipMessage = null }) {
                    MaterialText(text = "Close")
                }
            },
            text = {
                MaterialText(text = message)
            },
            title = {
                MaterialText(text = "Description")
            }
        )
    }
}

/**
 * Renders a single block with highlight support.
 */
@Composable
private fun HighlightableBlockRenderer(
    block: RichTextBlock,
    section: HighlightSection,
    highlights: List<TextHighlight>,
    baseOffset: Int,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    when (block) {
        is RichTextBlock.Paragraph -> {
            val blockHighlights = getHighlightsForRange(
                highlights, 
                baseOffset, 
                baseOffset + block.text.length
            ).map { it.adjustedForOffset(-baseOffset) }
            
            SelectableHighlightText(
                text = block.text,
                highlights = blockHighlights,
                onHighlightAdd = { start, end, text, color ->
                    onHighlightAdd(start + baseOffset, end + baseOffset, text, color)
                },
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick
            )
        }
        
        is RichTextBlock.Heading -> {
            // For now, headings are not highlightable (usually short)
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }
        
        is RichTextBlock.BulletList -> {
            // Render list items - for simplicity, use standard renderer
            // TODO: Add per-item highlight support
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }
        
        is RichTextBlock.OrderedList -> {
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }
        
        is RichTextBlock.CodeBlock -> {
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }
        
        is RichTextBlock.Table -> {
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }
        
        is RichTextBlock.AbstractBlock -> {
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }
        
        is RichTextBlock.Media -> {
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }
        
        RichTextBlock.Divider -> {
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }
    }
}

/**
 * Get the text length of a block for offset calculation.
 */
private fun getBlockTextLength(block: RichTextBlock): Int {
    return when (block) {
        is RichTextBlock.Paragraph -> block.text.length
        is RichTextBlock.Heading -> block.text.length
        is RichTextBlock.BulletList -> block.items.sumOf { it.length + 1 }
        is RichTextBlock.OrderedList -> block.items.sumOf { it.length + 1 }
        is RichTextBlock.CodeBlock -> block.text.length
        is RichTextBlock.Table -> 0 // Tables don't contribute to text offset
        is RichTextBlock.AbstractBlock -> block.blocks.sumOf { getBlockTextLength(it) + 1 }
        is RichTextBlock.Media -> 0
        RichTextBlock.Divider -> 0
    }
}

/**
 * Filter highlights that fall within a specific range.
 */
private fun getHighlightsForRange(
    highlights: List<TextHighlight>,
    start: Int,
    end: Int
): List<TextHighlight> {
    return highlights.filter { it.overlaps(start, end) }
}

/**
 * Adjust highlight offsets relative to a base offset.
 */
private fun TextHighlight.adjustedForOffset(offset: Int): TextHighlight {
    return copy(
        startOffset = (startOffset + offset).coerceAtLeast(0),
        endOffset = (endOffset + offset).coerceAtLeast(0)
    )
}

@Composable
private fun rememberLinkHandler(onLinkClick: ((String) -> Unit)?): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(onLinkClick, uriHandler) {
        onLinkClick ?: { url ->
            try {
                uriHandler.openUri(url)
            } catch (e: Exception) {
                println("HighlightableRichText: Failed to open URL '$url': ${e.message}")
            }
        }
    }
}

@Composable
private fun rememberMediaHandler(onMediaClick: ((String) -> Unit)?): (String) -> Unit {
    return remember(onMediaClick) { onMediaClick ?: {} }
}
