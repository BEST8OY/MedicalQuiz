package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.medicalquiz.app.shared.data.TextHighlightsRepository
import com.medicalquiz.app.shared.data.models.HighlightColor
import com.medicalquiz.app.shared.data.models.HighlightSection
import com.medicalquiz.app.shared.data.models.TextHighlight
import com.medicalquiz.app.shared.ui.richtext.parser.RichTextParser
import kotlin.math.max
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
    
    // Get highlights for this section - proper StateFlow collection with stable dependencies
    val questionHighlightsState = highlightsRepository?.questionHighlights?.collectAsState()
    val explanationHighlightsState = highlightsRepository?.explanationHighlights?.collectAsState()
    
    val highlights = when (section) {
        HighlightSection.QUESTION -> questionHighlightsState?.value ?: emptyList()
        HighlightSection.EXPLANATION -> explanationHighlightsState?.value ?: emptyList()
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
            // Render each list item as individually highlightable
            HighlightableBulletList(
                block = block,
                highlights = highlights,
                baseOffset = baseOffset,
                onHighlightAdd = onHighlightAdd,
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick
            )
        }

        is RichTextBlock.OrderedList -> {
            // Render each list item as individually highlightable
            HighlightableOrderedList(
                block = block,
                highlights = highlights,
                baseOffset = baseOffset,
                onHighlightAdd = onHighlightAdd,
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick
            )
        }
        
        is RichTextBlock.CodeBlock -> {
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }
        
        is RichTextBlock.Table -> {
            // Render table cells as individually highlightable
            HighlightableTable(
                block = block,
                highlights = highlights,
                baseOffset = baseOffset,
                onHighlightAdd = onHighlightAdd,
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick
            )
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
 * Renders a bullet list with per-item highlight support.
 */
@Composable
private fun HighlightableBulletList(
    block: RichTextBlock.BulletList,
    highlights: List<TextHighlight>,
    baseOffset: Int,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var itemOffset = baseOffset

        block.items.forEachIndexed { index, itemText ->
            val itemLength = itemText.length
            val itemHighlights = getHighlightsForRange(
                highlights,
                itemOffset,
                itemOffset + itemLength
            ).map { it.adjustedForOffset(-itemOffset) }

            Row(modifier = Modifier.fillMaxWidth()) {
                MaterialText(
                    text = "\u2022",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 12.dp)
                )
                HighlightableListItem(
                    text = itemText,
                    highlights = itemHighlights,
                    baseOffset = itemOffset,
                    onHighlightAdd = onHighlightAdd,
                    onHighlightRemove = onHighlightRemove,
                    onHighlightColorChange = onHighlightColorChange,
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick,
                    modifier = Modifier.weight(1f)
                )
            }

            // Move to next item ( +1 for separator)
            itemOffset += itemLength + 1
        }
    }
}

/**
 * Renders an ordered list with per-item highlight support.
 */
@Composable
private fun HighlightableOrderedList(
    block: RichTextBlock.OrderedList,
    highlights: List<TextHighlight>,
    baseOffset: Int,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var itemOffset = baseOffset

        block.items.forEachIndexed { index, itemText ->
            val itemLength = itemText.length
            val itemHighlights = getHighlightsForRange(
                highlights,
                itemOffset,
                itemOffset + itemLength
            ).map { it.adjustedForOffset(-itemOffset) }

            Row(modifier = Modifier.fillMaxWidth()) {
                MaterialText(
                    text = "${block.start + index}.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 12.dp)
                )
                HighlightableListItem(
                    text = itemText,
                    highlights = itemHighlights,
                    baseOffset = itemOffset,
                    onHighlightAdd = onHighlightAdd,
                    onHighlightRemove = onHighlightRemove,
                    onHighlightColorChange = onHighlightColorChange,
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick,
                    modifier = Modifier.weight(1f)
                )
            }

            // Move to next item (+1 for separator)
            itemOffset += itemLength + 1
        }
    }
}

/**
 * A single highlightable list item.
 */
@Composable
private fun HighlightableListItem(
    text: AnnotatedString,
    highlights: List<TextHighlight>,
    baseOffset: Int,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    SelectableHighlightText(
        text = text,
        highlights = highlights,
        onHighlightAdd = { start, end, selectedText, color ->
            onHighlightAdd(start + baseOffset, end + baseOffset, selectedText, color)
        },
        onHighlightRemove = onHighlightRemove,
        onHighlightColorChange = onHighlightColorChange,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick,
        modifier = modifier
    )
}

/**
 * Renders a table with per-cell highlight support.
 */
@Composable
private fun HighlightableTable(
    block: RichTextBlock.Table,
    highlights: List<TextHighlight>,
    baseOffset: Int,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    if (block.columnCount == 0) return

    val renderModel = remember(block) { block.toRenderModel() }
    val scrollState = rememberScrollState()
    val minTableWidth = 120.dp * renderModel.columnCount

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints {
            val tableWidth = max(minTableWidth, maxWidth)
            var cellOffset = baseOffset

            Column(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .width(tableWidth)
            ) {
                renderModel.rows.forEachIndexed { rowIndex, row ->
                    val effectiveRowClasses = row.classNames + block.classNames
                    val baseBackground = when {
                        row.isHeaderRow -> MaterialTheme.colorScheme.secondaryContainer
                        effectiveRowClasses.containsInsensitive("abstract") -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(baseBackground)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.cells.forEach { cell ->
                            val weight = cell.cell.width ?: cell.columnSpan.coerceAtLeast(1).toFloat()

                            if (!cell.isVisible) {
                                Spacer(modifier = Modifier.weight(weight))
                            } else {
                                val currentCellOffset = cellOffset
                                val cellLength = cell.cell.text.length
                                val cellHighlights = getHighlightsForRange(
                                    highlights,
                                    currentCellOffset,
                                    currentCellOffset + cellLength
                                ).map { it.adjustedForOffset(-currentCellOffset) }

                                val cellBackground = when {
                                    cell.cell.classNames.containsInsensitive("selected") -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    cell.cell.classNames.containsInsensitive("wichtig") -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> Color.Transparent
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(weight)
                                        .padding(horizontal = 4.dp),
                                    color = cellBackground,
                                    tonalElevation = if (cellBackground == Color.Transparent) 0.dp else 1.dp,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp)
                                            .padding(start = cell.cell.paddingStart),
                                        contentAlignment = when (cell.cell.alignment) {
                                            TextAlign.Center -> Alignment.Center
                                            TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                                            else -> Alignment.CenterStart
                                        }
                                    ) {
                                        SelectableHighlightText(
                                            text = cell.cell.text,
                                            highlights = cellHighlights,
                                            onHighlightAdd = { start, end, selectedText, color ->
                                                onHighlightAdd(
                                                    start + currentCellOffset,
                                                    end + currentCellOffset,
                                                    selectedText,
                                                    color
                                                )
                                            },
                                            onHighlightRemove = onHighlightRemove,
                                            onHighlightColorChange = onHighlightColorChange,
                                            onLinkClick = onLinkClick,
                                            onTooltipClick = onTooltipClick,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // Advance offset for this cell
                                cellOffset += cellLength
                            }
                        }
                    }

                    if (rowIndex != renderModel.rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }
            }
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
        is RichTextBlock.Table -> {
            // Calculate text length using the render model to properly handle rowspan/colspan
            // Only count VISIBLE cells to match what we actually render
            val renderModel = block.toRenderModel()
            renderModel.rows.sumOf { row ->
                row.cells.sumOf { cell ->
                    if (cell.isVisible) cell.cell.text.length else 0
                }
            }
        }
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
