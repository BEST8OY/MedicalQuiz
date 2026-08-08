package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.max
import androidx.compose.ui.text.TextStyle
import kotlin.collections.ArrayDeque
import kotlin.collections.buildList
import kotlin.math.max
import kotlin.math.roundToInt
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.ui.theme.Stroke

/** Maximum iterations per row to prevent infinite loops from malformed HTML */
private const val MAX_COLUMN_ITERATIONS = 500

internal sealed interface TableLayoutItem {
    data class RowItem(val rowIndex: Int, val row: TableRenderedRow) : TableLayoutItem
    data object DividerItem : TableLayoutItem
}

/**
 * Shared layout shell for tables: a bordered [Surface] containing a horizontally
 * scrolling column of rows separated by dividers. Both [RichTextTable] and
 * [HighlightableTable] route through this so layout cannot drift between them.
 *
 * Rowspan anchor cells are rendered as overlays that span the full height of
 * their row range, with content vertically centered.
 *
 * @param block The table block to render
 * @param renderRow Renders a single row (anchor cells render as transparent placeholders)
 * @param renderAnchorContent Renders the actual content for a rowspan anchor cell
 */
@Composable
internal fun RichTextTableShell(
    block: RichTextBlock.Table,
    renderRow: @Composable (row: TableRenderedRow, rowIndex: Int) -> Unit,
    renderAnchorContent: (@Composable (cell: TableRenderedCell, rowIndex: Int, cellIndex: Int) -> Unit)? = null
) {
    val renderModel = remember(block) { block.toRenderModel() }
    if (renderModel.columnCount == 0) return
    val scrollState = rememberScrollState()
    val minTableWidth = Layout.TableMinCellWidth * renderModel.columnCount

    // Collect rowspan anchors
    val anchors = remember(renderModel) {
        buildList {
            renderModel.rows.forEachIndexed { rowIndex, row ->
                row.cells.forEachIndexed { cellIndex, cell ->
                    if (cell.isVisible && cell.rowSpan > 1) {
                        add(RowspanAnchorInfo(
                            cell = cell,
                            cellIndex = cellIndex,
                            startRow = rowIndex,
                            endRow = (rowIndex + cell.rowSpan - 1).coerceAtMost(renderModel.rows.lastIndex)
                        ))
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(Stroke.Thin, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints {
            val tableWidth = max(minTableWidth, maxWidth)

            if (anchors.isEmpty()) {
                // No rowspan anchors — simple Column
                Column(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .width(tableWidth)
                ) {
                    renderModel.rows.forEachIndexed { index, row ->
                        renderRow(row, index)
                        if (index != renderModel.rows.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            } else if (renderAnchorContent != null) {
                // Has rowspan anchors — two-pass SubcomposeLayout
                SubcomposeLayout(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .width(tableWidth)
                ) { constraints ->
                    val layoutItems = buildList {
                        renderModel.rows.forEachIndexed { index, row ->
                            add(TableLayoutItem.RowItem(index, row))
                            if (index != renderModel.rows.lastIndex) {
                                add(TableLayoutItem.DividerItem)
                            }
                        }
                    }

                    // Measure all rows and dividers flat-mapped
                    val rowMeasurables = subcompose("rows") {
                        layoutItems.forEach { item ->
                            when (item) {
                                is TableLayoutItem.RowItem -> renderRow(item.row, item.rowIndex)
                                TableLayoutItem.DividerItem -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                    val rowPlacements = rowMeasurables.map { it.measure(constraints) }
                    val totalHeight = rowPlacements.sumOf { it.height }

                    // Compute per-row positions and heights
                    val rowPositions = IntArray(renderModel.rows.size)
                    val rowHeights = IntArray(renderModel.rows.size)
                    var currentY = 0
                    layoutItems.forEachIndexed { itemIndex, item ->
                        val height = rowPlacements[itemIndex].height
                        if (item is TableLayoutItem.RowItem) {
                            rowPositions[item.rowIndex] = currentY
                            rowHeights[item.rowIndex] = height
                        }
                        currentY += height
                    }

                    // Measure anchor overlays with calculated widths and heights
                    val anchorMeasurables = subcompose("anchors") {
                        anchors.forEach { anchor ->
                            val startRowModel = renderModel.rows[anchor.startRow]
                            val totalWeight = startRowModel.cells.sumOf {
                                (it.cell.width ?: it.columnSpan.coerceAtLeast(1).toFloat()).toDouble()
                            }.toFloat()
                            val startWeight = startRowModel.cells.take(anchor.cellIndex).sumOf {
                                (it.cell.width ?: it.columnSpan.coerceAtLeast(1).toFloat()).toDouble()
                            }.toFloat()
                            val cellWeight = anchor.cell.cell.width ?: anchor.cell.columnSpan.coerceAtLeast(1).toFloat()

                            val insetSmPx = Inset.Small.toPx()
                            val usableWidth = tableWidth.toPx() - insetSmPx * 2

                            val isLeftEdge = startWeight == 0f
                            val isRightEdge = (startWeight + cellWeight) == totalWeight

                            val leftX = if (isLeftEdge) {
                                0f
                            } else {
                                insetSmPx + usableWidth * (startWeight / totalWeight)
                            }

                            val rightX = if (isRightEdge) {
                                tableWidth.toPx()
                            } else {
                                insetSmPx + usableWidth * ((startWeight + cellWeight) / totalWeight)
                            }

                            val cellWidth = (rightX - leftX).toDp()

                            val paddingStart = if (isLeftEdge) Inset.Small + Spacing.ExtraSmall * 2 else Spacing.ExtraSmall * 2
                            val paddingEnd = if (isRightEdge) Inset.Small + Spacing.ExtraSmall * 2 else Spacing.ExtraSmall * 2

                            val isAbstractRow = (startRowModel.classNames + block.classNames).containsInsensitive("abstract")
                            val baseBackground = when {
                                startRowModel.isHeaderRow -> MaterialTheme.colorScheme.surfaceContainerHighest
                                isAbstractRow -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val cellBackground = when {
                                anchor.cell.cell.classNames.containsInsensitive("selected") -> MaterialTheme.colorScheme.secondaryContainer
                                anchor.cell.cell.classNames.containsInsensitive("wichtig") -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> baseBackground
                            }

                            Surface(
                                modifier = Modifier
                                    .width(cellWidth),
                                color = cellBackground,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = paddingStart, end = paddingEnd)
                                        .padding(start = anchor.cell.cell.paddingStart),
                                    contentAlignment = when (anchor.cell.cell.alignment) {
                                        TextAlign.Center -> Alignment.Center
                                        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                                        else -> Alignment.CenterStart
                                    }
                                ) {
                                    renderAnchorContent(anchor.cell, anchor.startRow, anchor.cellIndex)
                                }
                            }
                        }
                    }

                    val anchorPlacements = anchorMeasurables.mapIndexed { i, measurable ->
                        val anchor = anchors[i]

                        val startRowModel = renderModel.rows[anchor.startRow]
                        val totalWeight = startRowModel.cells.sumOf {
                            (it.cell.width ?: it.columnSpan.coerceAtLeast(1).toFloat()).toDouble()
                        }.toFloat()
                        val startWeight = startRowModel.cells.take(anchor.cellIndex).sumOf {
                            (it.cell.width ?: it.columnSpan.coerceAtLeast(1).toFloat()).toDouble()
                        }.toFloat()
                        val cellWeight = anchor.cell.cell.width ?: anchor.cell.columnSpan.coerceAtLeast(1).toFloat()

                        val insetSmPx = Inset.Small.toPx()
                        val usableWidth = tableWidth.toPx() - insetSmPx * 2

                        val isLeftEdge = startWeight == 0f
                        val isRightEdge = (startWeight + cellWeight) == totalWeight

                        val leftX = if (isLeftEdge) {
                            0f
                        } else {
                            insetSmPx + usableWidth * (startWeight / totalWeight)
                        }

                        val rightX = if (isRightEdge) {
                            tableWidth.toPx()
                        } else {
                            insetSmPx + usableWidth * ((startWeight + cellWeight) / totalWeight)
                        }

                        val cellWidthPx = rightX - leftX

                        val spanHeight = (anchor.startRow..anchor.endRow)
                            .sumOf { rowHeights.getOrElse(it) { 0 } }
                        measurable.measure(constraints.copy(
                            minWidth = cellWidthPx.roundToInt(),
                            maxWidth = cellWidthPx.roundToInt(),
                            minHeight = spanHeight,
                            maxHeight = spanHeight
                        ))
                    }

                    // Place everything
                    layout(constraints.maxWidth, totalHeight) {
                        // Place rows and dividers
                        var y = 0
                        rowPlacements.forEach { placement ->
                            placement.place(0, y)
                            y += placement.height
                        }
                        // Place anchor overlays
                        anchors.forEachIndexed { i, anchor ->
                            val startY = rowPositions[anchor.startRow]

                            val startRowModel = renderModel.rows[anchor.startRow]
                            val totalWeight = startRowModel.cells.sumOf {
                                (it.cell.width ?: it.columnSpan.coerceAtLeast(1).toFloat()).toDouble()
                            }.toFloat()
                            val startWeight = startRowModel.cells.take(anchor.cellIndex).sumOf {
                                (it.cell.width ?: it.columnSpan.coerceAtLeast(1).toFloat()).toDouble()
                            }.toFloat()

                            val insetSmPx = Inset.Small.toPx()
                            val usableWidth = tableWidth.toPx() - insetSmPx * 2

                            val isLeftEdge = startWeight == 0f
                            val leftX = if (isLeftEdge) {
                                0f
                            } else {
                                insetSmPx + usableWidth * (startWeight / totalWeight)
                            }

                            anchorPlacements[i].place(leftX.roundToInt(), startY)
                        }
                    }
                }
            } else {
                // Has anchors but no renderer — simple Column (fallback)
                Column(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .width(tableWidth)
                ) {
                    renderModel.rows.forEachIndexed { index, row ->
                        renderRow(row, index)
                        if (index != renderModel.rows.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

private class RowspanAnchorInfo(
    val cell: TableRenderedCell,
    val cellIndex: Int,
    val startRow: Int,
    val endRow: Int
)

/**
 * Renders a table with support for rowspan/colspan.
 *
 * @param block The table block containing rows and column information
 * @param onLinkClick Callback for link clicks within table cells
 * @param onTooltipClick Optional callback for tooltip interactions
 */
@Composable
internal fun RichTextTable(
    block: RichTextBlock.Table,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
) {
    val renderModel = remember(block) { block.toRenderModel() }
    RichTextTableShell(
        block = block,
        renderRow = { row, _ ->
            TableRowContent(
                row = row,
                tableClassNames = block.classNames,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
                isRowspanOverlayEnabled = true
            )
        },
        renderAnchorContent = { cell, rowIndex, _ ->
            val rowIndexModel = renderModel.rows.getOrNull(rowIndex)
            val isHeaderRow = rowIndexModel?.isHeaderRow ?: false
            val isHeaderCell = isHeaderRow || cell.cell.isHeader
            val isAbstractRow = (rowIndexModel?.classNames ?: emptySet()).containsInsensitive("abstract")
            val textStyle = tableCellTextStyle(isHeaderCell)
            val textColor = resolveCellTextColor(
                isHeaderCell = isHeaderCell,
                isAbstractRow = isAbstractRow,
                cellClassNames = cell.cell.classNames
            )
            InteractiveText(
                text = cell.cell.text,
                modifier = Modifier,
                style = textStyle,
                color = textColor,
                textAlign = cell.cell.alignment,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible
            )
        }
    )
}

/**
 * Represents a table prepared for rendering with processed rowspan/colspan.
 */
internal data class TableRenderModel(
    val rows: List<TableRenderedRow>,
    val columnCount: Int
)

internal data class TableRenderedRow(
    val cells: List<TableRenderedCell>,
    val isHeaderRow: Boolean,
    val classNames: Set<String>
)

internal data class TableRenderedCell(
    val cell: RichTextTableCell,
    val columnSpan: Int,
    val rowSpan: Int,
    val isVisible: Boolean,
    val isRowspanEnd: Boolean = false
)

/**
 * Converts a table block to a render model, processing rowspan and colspan.
 */
internal fun RichTextBlock.Table.toRenderModel(): TableRenderModel {
    val builder = TableGridBuilder()
    val orderedRows = buildList {
        addAll(headerRows)
        addAll(bodyRows)
    }
    val renderedRows = orderedRows.map { builder.renderRow(it) }
    val columnCount = maxOf(columnCount, builder.columnCount)
    return TableRenderModel(rows = renderedRows, columnCount = columnCount)
}

private class TableGridBuilder {
    private val spanSlots = mutableListOf<ColumnSpan?>()
    var columnCount: Int = 0
        private set

    fun renderRow(row: RichTextTableRow): TableRenderedRow {
        val pendingCells = ArrayDeque(row.cells)
        val renderedCells = mutableListOf<TableRenderedCell>()
        var columnIndex = 0
        // Safety limit to prevent infinite loops from malformed HTML
        val maxIterations = MAX_COLUMN_ITERATIONS
        var iterations = 0
        while ((pendingCells.isNotEmpty() || hasAnchorsFrom(columnIndex)) && iterations < maxIterations) {
            iterations++
            when (val occupancy = spanSlots.getOrNull(columnIndex)) {
                is ColumnSpan.Anchor -> {
                    val tracker = occupancy.tracker
                    val isEnd = tracker.remainingRows == 1
                    renderedCells += TableRenderedCell(
                        cell = tracker.cell,
                        columnSpan = tracker.spanWidth,
                        rowSpan = tracker.cell.rowSpan,
                        isVisible = false,
                        isRowspanEnd = isEnd
                    )
                    tracker.remainingRows -= 1
                    if (tracker.remainingRows == 0) {
                        clearTracker(tracker)
                    }
                    columnIndex += tracker.spanWidth
                }
                is ColumnSpan.Continuation -> {
                    columnIndex += 1
                }
                null -> {
                    val cell = if (pendingCells.isEmpty()) {
                        columnIndex += 1
                        continue
                    } else {
                        pendingCells.removeFirst()
                    }
                    val spanWidth = cell.columnSpan.coerceAtLeast(1)
                    ensureSlots(columnIndex + spanWidth)
                    renderedCells += TableRenderedCell(
                        cell = cell,
                        columnSpan = spanWidth,
                        rowSpan = cell.rowSpan,
                        isVisible = true,
                        isRowspanEnd = cell.rowSpan == 1
                    )
                    if (cell.rowSpan > 1) {
                        val tracker = RowSpanTracker(
                            cell = cell,
                            remainingRows = cell.rowSpan - 1,
                            spanWidth = spanWidth,
                            startColumn = columnIndex
                        )
                        spanSlots[columnIndex] = ColumnSpan.Anchor(tracker)
                        for (offset in 1 until spanWidth) {
                            spanSlots[columnIndex + offset] = ColumnSpan.Continuation(tracker)
                        }
                    }
                    columnIndex += spanWidth
                }
            }
        }
        columnCount = max(columnCount, columnIndex)
        return TableRenderedRow(
            cells = renderedCells,
            isHeaderRow = row.isHeader,
            classNames = row.classNames
        )
    }

    private fun hasAnchorsFrom(startIndex: Int): Boolean {
        for (index in startIndex until spanSlots.size) {
            if (spanSlots[index] is ColumnSpan.Anchor) return true
        }
        return false
    }

    private fun ensureSlots(requiredSize: Int) {
        if (requiredSize <= spanSlots.size) return
        repeat(requiredSize - spanSlots.size) { spanSlots.add(null) }
    }

    private fun clearTracker(tracker: RowSpanTracker) {
        val end = tracker.startColumn + tracker.spanWidth
        for (index in tracker.startColumn until end) {
            if (index in spanSlots.indices) {
                spanSlots[index] = null
            }
        }
    }

    private sealed interface ColumnSpan {
        val tracker: RowSpanTracker

        data class Anchor(override val tracker: RowSpanTracker) : ColumnSpan
        data class Continuation(override val tracker: RowSpanTracker) : ColumnSpan
    }

    private data class RowSpanTracker(
        val cell: RichTextTableCell,
        var remainingRows: Int,
        val spanWidth: Int,
        val startColumn: Int
    )
}

/**
 * Renders a single table row with proper styling based on header status, classes,
 * and zebra position. Headers use the neutral surface tier so that
 * `secondaryContainer`/`tertiaryContainer` remain visually distinct when used by
 * the "selected"/"wichtig" cell classes.
 *
 * @param row The rendered row data
 * @param tableClassNames Class names from the parent table element
 * @param visibleRowIndex 0-based index among visible body rows (for zebra striping)
 * @param onLinkClick Callback for link clicks
 * @param onTooltipClick Optional callback for tooltips
 * @param customCellContent Optional slot overriding how a visible cell is drawn
 */
@Composable
internal fun TableRowContent(
    row: TableRenderedRow,
    tableClassNames: Set<String>,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    isRowspanOverlayEnabled: Boolean = false,
    customCellContent: (@Composable (cell: TableRenderedCell, textStyle: TextStyle, cellIndex: Int) -> Unit)? = null
) {
    val effectiveRowClasses = row.classNames + tableClassNames
    val isAbstractRow = effectiveRowClasses.containsInsensitive("abstract")
    val isRowspanContinuation = row.cells.any { !it.isVisible }
    val baseBackground = when {
        isRowspanContinuation -> Color.Transparent
        row.isHeaderRow -> MaterialTheme.colorScheme.surfaceContainerHighest
        isAbstractRow -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseBackground)
            .padding(horizontal = Inset.Small, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        row.cells.forEachIndexed { cellIndex, cell ->
            val weight = cell.cell.width ?: cell.columnSpan.coerceAtLeast(1).toFloat()
            if (!cell.isVisible || (isRowspanOverlayEnabled && cell.rowSpan > 1)) {
                Spacer(modifier = Modifier.weight(weight))
            } else {
                val isHeaderCell = row.isHeaderRow || cell.cell.isHeader
                val textStyle = tableCellTextStyle(isHeaderCell)
                val textColor = resolveCellTextColor(
                    isHeaderCell = isHeaderCell,
                    isAbstractRow = isAbstractRow,
                    cellClassNames = cell.cell.classNames
                )
                val cellBackground = when {
                    cell.cell.classNames.containsInsensitive("selected") -> MaterialTheme.colorScheme.secondaryContainer
                    cell.cell.classNames.containsInsensitive("wichtig") -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> Color.Transparent
                }
                Surface(
                    modifier = Modifier
                        .weight(weight)
                        .padding(horizontal = Spacing.ExtraSmall),
                    color = cellBackground,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.ExtraSmall)
                            .padding(start = cell.cell.paddingStart),
                        contentAlignment = when (cell.cell.alignment) {
                            TextAlign.Center -> Alignment.Center
                            TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                            else -> Alignment.CenterStart
                        }
                    ) {
                        if (customCellContent != null) {
                            customCellContent(cell, textStyle, cellIndex)
                        } else {
                            InteractiveText(
                                text = cell.cell.text,
                                modifier = Modifier,
                                style = textStyle,
                                color = textColor,
                                textAlign = cell.cell.alignment,
                                onLinkClick = onLinkClick,
                                onTooltipClick = onTooltipClick,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Visible
                            )
                        }
                    }
                }
            }
        }
    }
}
