package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.ui.richtext.parser.RichTextParser
import com.medqb.app.shared.domain.SnackbarMessage
import androidx.compose.material3.Text as MaterialText

/**
 * Length of the implicit separator between blocks in the global offset space.
 * Must match the separator used during text construction in the parser.
 * Currently each block is separated by a single newline character.
 *
 * IMPORTANT: If the parser ever changes separator length (e.g. to "\n\n"),
 * every highlight offset will silently desync. A mismatch here causes
 * highlights to drift by N-1 chars per block boundary.
 */
private const val BLOCK_SEPARATOR_LENGTH = 1

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
    onTooltipClick: ((String) -> Unit)? = null,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
) {
    val palette = rememberRichTextPalette()
    val blocks = remember(html, palette, showSelectedHighlight) {
        if (html.trim().isEmpty()) emptyList()
        else RichTextParser.parse(html.trim(), palette, showSelectedHighlight)
    }
    
    // Get highlights for this section - proper StateFlow collection with stable dependencies
    val questionHighlightsState = highlightsRepository?.questionHighlights?.collectAsStateWithLifecycle()
    val explanationHighlightsState = highlightsRepository?.explanationHighlights?.collectAsStateWithLifecycle()
    
    val highlights = when (section) {
        HighlightSection.QUESTION -> questionHighlightsState?.value ?: emptyList()
        HighlightSection.EXPLANATION -> explanationHighlightsState?.value ?: emptyList()
    }
    
    val resolvedLinkHandler = rememberResolvedLinkHandler(onLinkClick, sourceTag = "HighlightableRichText")
    val resolvedMediaHandler = rememberResolvedMediaHandler(onMediaClick)
    val tooltipSupport = rememberRichTextTooltipSupport(
        resetKey = blocks,
        onTooltipClick = onTooltipClick
    )
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.MediumSmall)
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
                onTooltipClick = tooltipSupport.onTooltipClick,
                onShowSnackbar = onShowSnackbar
            )
            
            cumulativeOffset += getBlockTextLength(block) + BLOCK_SEPARATOR_LENGTH
        }
    }
    
    RichTextTooltipBottomSheet(
        content = tooltipSupport.tooltipContent,
        onDismissRequest = tooltipSupport.dismissTooltip
    )
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
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit
) {
    when (block) {
        is RichTextBlock.Paragraph -> {
            val blockHighlights = remember(highlights, baseOffset, block.text.length) {
                mapHighlightsToLocal(
                    highlights = highlights,
                    start = baseOffset,
                    end = baseOffset + block.text.length
                )
            }
            
            SelectableHighlightText(
                text = block.text,
                highlights = blockHighlights,
                onHighlightAdd = mapOnHighlightAddToGlobal(baseOffset, onHighlightAdd),
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
                onShowSnackbar = onShowSnackbar
            )
        }

        is RichTextBlock.Heading -> {
            // For now, headings are not highlightable (usually short)
            RichTextBlockRenderer(block, onLinkClick, onMediaClick, onTooltipClick)
        }

        is RichTextBlock.BulletList -> {
            // Render each list item as individually highlightable
            HighlightableList(
                items = block.items,
                markerProvider = { _ -> "\u2022" },
                highlights = highlights,
                baseOffset = baseOffset,
                onHighlightAdd = onHighlightAdd,
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
                onShowSnackbar = onShowSnackbar
            )
        }

        is RichTextBlock.OrderedList -> {
            // Render each list item as individually highlightable
            HighlightableList(
                items = block.items,
                markerProvider = { index -> "${block.start + index}." },
                highlights = highlights,
                baseOffset = baseOffset,
                onHighlightAdd = onHighlightAdd,
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
                onShowSnackbar = onShowSnackbar
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
                onTooltipClick = onTooltipClick,
                onShowSnackbar = onShowSnackbar
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
 * Renders a list with per-item highlight support.
 */
@Composable
private fun HighlightableList(
    items: List<AnnotatedString>,
    markerProvider: (index: Int) -> String,
    highlights: List<TextHighlight>,
    baseOffset: Int,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit
) {
    val richTextScale = LocalRichTextScale.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        var itemOffset = baseOffset

        items.forEachIndexed { index, itemText ->
            val itemLength = itemText.length
            val itemHighlights = remember(highlights, itemOffset, itemLength) {
                mapHighlightsToLocal(
                    highlights = highlights,
                    start = itemOffset,
                    end = itemOffset + itemLength
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                MaterialText(
                    text = markerProvider(index),
                    style = MaterialTheme.typography.bodyLarge.scaledBy(richTextScale.proseScale),
                    modifier = Modifier.padding(end = Inset.Small),
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
                    onShowSnackbar = onShowSnackbar,
                    modifier = Modifier.weight(1f)
                )
            }

            // Move to next item
            itemOffset += itemLength + BLOCK_SEPARATOR_LENGTH
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
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    SelectableHighlightText(
        text = text,
        highlights = highlights,
        onHighlightAdd = mapOnHighlightAddToGlobal(baseOffset, onHighlightAdd),
        onHighlightRemove = onHighlightRemove,
        onHighlightColorChange = onHighlightColorChange,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick,
        onShowSnackbar = onShowSnackbar,
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
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit
) {
    if (block.columnCount == 0) return

    val renderModel = remember(block) { block.toRenderModel() }
    val cellBaseOffsets = remember(renderModel, baseOffset) {
        buildTableCellBaseOffsets(renderModel, baseOffset)
    }

    RichTextTableShell(
        block = block,
        renderRow = { row, rowIndex ->
            val isAbstractRow = row.classNames.containsInsensitive("abstract")
            TableRowContent(
                row = row,
                tableClassNames = block.classNames,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
                isRowspanOverlayEnabled = true,
                customCellContent = { cell, cellTextStyle, cellIndex ->
                    HighlightableTableCell(
                        cell = cell,
                        cellTextStyle = cellTextStyle,
                        cellIndex = cellIndex,
                        rowIndex = rowIndex,
                        cellBaseOffsets = cellBaseOffsets,
                        highlights = highlights,
                        onHighlightAdd = onHighlightAdd,
                        onHighlightRemove = onHighlightRemove,
                        onHighlightColorChange = onHighlightColorChange,
                        onLinkClick = onLinkClick,
                        onTooltipClick = onTooltipClick,
                        onShowSnackbar = onShowSnackbar,
                        isAbstractRow = isAbstractRow
                    )
                }
            )
        },
        renderAnchorContent = { cell, rowIndex, cellIndex ->
            val rowIndexModel = renderModel.rows.getOrNull(rowIndex)
            val isHeaderRow = rowIndexModel?.isHeaderRow ?: false
            val isHeaderCell = isHeaderRow || cell.cell.isHeader
            val isAbstractRow = (rowIndexModel?.classNames ?: emptySet()).containsInsensitive("abstract")
            val textStyle = tableCellTextStyle(isHeaderCell)
            HighlightableTableCell(
                cell = cell,
                cellTextStyle = textStyle,
                cellIndex = cellIndex,
                rowIndex = rowIndex,
                cellBaseOffsets = cellBaseOffsets,
                highlights = highlights,
                onHighlightAdd = onHighlightAdd,
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
                onShowSnackbar = onShowSnackbar,
                isAbstractRow = isAbstractRow
            )
        }
    )
}

/**
 * Shared highlightable cell rendering for both regular and anchor cells.
 */
@Composable
private fun HighlightableTableCell(
    cell: TableRenderedCell,
    cellTextStyle: TextStyle,
    cellIndex: Int,
    rowIndex: Int,
    cellBaseOffsets: List<List<Int?>>,
    highlights: List<TextHighlight>,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit,
    isAbstractRow: Boolean = false
) {
    if (!cell.isVisible) return

    val currentCellOffset = cellBaseOffsets.getOrNull(rowIndex)?.getOrNull(cellIndex)
    if (currentCellOffset == null) return

    val isHeaderCell = cell.cell.isHeader
    val textColor = resolveCellTextColor(
        isHeaderCell = isHeaderCell,
        isAbstractRow = isAbstractRow,
        cellClassNames = cell.cell.classNames
    )

    val cellLength = cell.cell.text.length
    val cellHighlights = remember(highlights, currentCellOffset, cellLength) {
        mapHighlightsToLocal(
            highlights = highlights,
            start = currentCellOffset,
            end = currentCellOffset + cellLength
        )
    }

    SelectableHighlightText(
        text = cell.cell.text,
        highlights = cellHighlights,
        textStyle = cellTextStyle.copy(textAlign = cell.cell.alignment),
        color = textColor,
        onHighlightAdd = mapOnHighlightAddToGlobal(currentCellOffset, onHighlightAdd),
        onHighlightRemove = onHighlightRemove,
        onHighlightColorChange = onHighlightColorChange,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick,
        onShowSnackbar = onShowSnackbar,
        modifier = Modifier.fillMaxWidth()
    )
}


private fun buildTableCellBaseOffsets(
    renderModel: TableRenderModel,
    baseOffset: Int
): List<List<Int?>> {
    var runningOffset = baseOffset
    return renderModel.rows.map { row ->
        row.cells.map { cell ->
            if (!cell.isVisible) {
                null
            } else {
                val start = runningOffset
                runningOffset += cell.cell.text.length
                start
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
        is RichTextBlock.BulletList -> block.items.sumOf { it.length + BLOCK_SEPARATOR_LENGTH }
        is RichTextBlock.OrderedList -> block.items.sumOf { it.length + BLOCK_SEPARATOR_LENGTH }
        is RichTextBlock.CodeBlock -> block.text.length
        is RichTextBlock.Table -> {
            (block.headerRows + block.bodyRows).sumOf { row ->
                row.cells.sumOf { it.text.length }
            }
        }
        is RichTextBlock.AbstractBlock -> block.blocks.sumOf { getBlockTextLength(it) + BLOCK_SEPARATOR_LENGTH }
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

private fun mapHighlightsToLocal(
    highlights: List<TextHighlight>,
    start: Int,
    end: Int
): List<TextHighlight> {
    return getHighlightsForRange(highlights, start, end)
        .mapNotNull { highlight ->
            val localStart = (kotlin.math.max(highlight.startOffset, start) - start).coerceAtLeast(0)
            val localEnd = (kotlin.math.min(highlight.endOffset, end) - start).coerceAtLeast(localStart)
            if (localEnd <= localStart) return@mapNotNull null
            highlight.copy(
                startOffset = localStart,
                endOffset = localEnd
            )
        }
}

private fun mapOnHighlightAddToGlobal(
    baseOffset: Int,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit
): (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit {
    return { start, end, text, color ->
        onHighlightAdd(start + baseOffset, end + baseOffset, text, color)
    }
}
