package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.medqb.app.shared.ui.richtext.parser.RichTextParserConfig
import kotlin.math.max
import kotlin.math.roundToInt

/** Maximum iterations per row to prevent infinite loops from malformed HTML */
private const val MAX_COLUMN_ITERATIONS = 500

internal sealed interface TableLayoutItem {
    data class RowItem(val rowIndex: Int, val row: TableRenderedRow) : TableLayoutItem
    data class DividerItem(val afterRowIndex: Int) : TableLayoutItem
}

/**
 * Unified table renderer supporting optional per-cell highlighting and rowspan overlays.
 */
@Composable
internal fun RichTextTable(
    block: RichTextBlock.Table,
    blockIndex: Int = 0,
    showSelectedHighlight: Boolean = false,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    val renderModel = remember(block) { block.toRenderModel() }
    if (renderModel.columnCount == 0) return

    RichTextTableShell(
        renderModel = renderModel,
        tableClassNames = block.classNames,
        showSelectedHighlight = showSelectedHighlight,
        renderRow = { row, rowIndex ->
            TableRowContent(
                row = row,
                tableClassNames = block.classNames,
                columnWeights = renderModel.columnWeights,
                showSelectedHighlight = showSelectedHighlight,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
                isRowspanOverlayEnabled = true,
                customCellContent = if (highlightConfig != null) {
                    { cell, textStyle, textColor, cellIndex ->
                        HighlightableRichTextContent(
                            text = cell.cell.text,
                            blockKey = "td:$blockIndex:$rowIndex:$cellIndex",
                            highlightConfig = highlightConfig,
                            style = textStyle,
                            color = textColor,
                            textAlign = cell.cell.alignment,
                            modifier = Modifier.fillMaxWidth(),
                            onLinkClick = onLinkClick,
                            onTooltipClick = onTooltipClick,
                        )
                    }
                } else null
            )
        },
        renderAnchorContent = { cell, rowIndex, cellIndex ->
            val rowModel = renderModel.rows.getOrNull(rowIndex)
            val isHeaderRow = rowModel?.isHeaderRow ?: false
            val isHeaderCell = isHeaderRow || cell.cell.isHeader
            val isAbstractRow = ((rowModel?.classNames ?: emptySet()) + block.classNames).containsInsensitive("abstract")
            val textStyle = tableCellTextStyle(isHeaderCell, cell.cell.classNames)
            val textColor = resolveCellTextColor(
                isHeaderCell = isHeaderCell,
                isAbstractRow = isAbstractRow,
                cellClassNames = cell.cell.classNames,
                showSelectedHighlight = showSelectedHighlight,
            )
            HighlightableRichTextContent(
                text = cell.cell.text,
                blockKey = "td:$blockIndex:$rowIndex:$cellIndex",
                highlightConfig = highlightConfig,
                style = textStyle,
                color = textColor,
                textAlign = cell.cell.alignment,
                modifier = if (highlightConfig != null) Modifier.fillMaxWidth() else Modifier,
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible,
            )
        }
    )
}

@Composable
private fun RichTextTableShell(
    renderModel: TableRenderModel,
    tableClassNames: Set<String>,
    showSelectedHighlight: Boolean,
    renderRow: @Composable (row: TableRenderedRow, rowIndex: Int) -> Unit,
    renderAnchorContent: @Composable (cell: TableRenderedCell, rowIndex: Int, cellIndex: Int) -> Unit,
) {
    if (renderModel.columnCount == 0) return
    val scrollState = rememberScrollState()
    val highlightHostState = LocalRichTextHighlightHostState.current
    LaunchedEffect(scrollState.isScrollInProgress, highlightHostState) {
        if (scrollState.isScrollInProgress) {
            highlightHostState?.dismiss()
        }
    }
    val columnMinWidth = if (renderModel.columnCount >= 5) 96.dp else 120.dp
    val minTableWidth = columnMinWidth * renderModel.columnCount
    val surfaceColor = MaterialTheme.colorScheme.surface

    val anchors = remember(renderModel) {
        buildList {
            renderModel.rows.forEachIndexed { rowIndex, row ->
                row.cells.forEachIndexed { cellIndex, cell ->
                    val endRow = (rowIndex + cell.rowSpan - 1).coerceAtMost(renderModel.rows.lastIndex)
                    if (cell.isVisible && cell.rowSpan > 1 && endRow > rowIndex) {
                        add(
                            RowspanAnchorInfo(
                                cell = cell,
                                cellIndex = cellIndex,
                                startRow = rowIndex,
                                endRow = endRow
                            )
                        )
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints(
            modifier = Modifier.drawWithContent {
                drawContent()
                val fadeWidth = 20.dp.toPx().coerceAtMost(size.width * 0.15f)
                if (fadeWidth > 0f && scrollState.canScrollBackward) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(surfaceColor.copy(alpha = 0.85f), Color.Transparent),
                            startX = 0f,
                            endX = fadeWidth,
                        ),
                        topLeft = Offset.Zero,
                        size = Size(fadeWidth, size.height),
                    )
                }
                if (fadeWidth > 0f && scrollState.canScrollForward) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, surfaceColor.copy(alpha = 0.85f)),
                            startX = size.width - fadeWidth,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - fadeWidth, 0f),
                        size = Size(fadeWidth, size.height),
                    )
                }
            }
        ) {
            val tableWidth = max(minTableWidth, maxWidth)

            if (anchors.isEmpty()) {
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
            } else {
                SubcomposeLayout(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .width(tableWidth)
                ) { constraints ->
                    val layoutItems = buildList {
                        renderModel.rows.forEachIndexed { index, row ->
                            add(TableLayoutItem.RowItem(index, row))
                            if (index != renderModel.rows.lastIndex) {
                                add(TableLayoutItem.DividerItem(index))
                            }
                        }
                    }

                    // 1. Measure rows & dividers
                    val rowMeasurables = subcompose("rows") {
                        layoutItems.forEach { item ->
                            when (item) {
                                is TableLayoutItem.RowItem -> renderRow(item.row, item.rowIndex)
                                is TableLayoutItem.DividerItem -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                    val rowPlacements = rowMeasurables.map { it.measure(constraints) }

                    val rowHeights = IntArray(renderModel.rows.size)
                    val dividerAfterHeights = IntArray(renderModel.rows.size)
                    layoutItems.forEachIndexed { itemIndex, item ->
                        val h = rowPlacements[itemIndex].height
                        when (item) {
                            is TableLayoutItem.RowItem -> rowHeights[item.rowIndex] = h
                            is TableLayoutItem.DividerItem -> dividerAfterHeights[item.afterRowIndex] = h
                        }
                    }

                    val tableWidthPx = tableWidth.toPx()
                    val insetSmPx = 12.dp.toPx()
                    val anchorBoundsList = anchors.map { anchor ->
                        computeAnchorHorizontalBounds(
                            row = renderModel.rows[anchor.startRow],
                            cellIndex = anchor.cellIndex,
                            tableWidthPx = tableWidthPx,
                            insetSmPx = insetSmPx,
                            columnWeights = renderModel.columnWeights,
                        )
                    }

                    // 2. Measure stateful anchor contents unconstrained in height so they never clip
                    //    and never get re-subcomposed.
                    val anchorMeasurables = subcompose("anchors") {
                        anchors.forEachIndexed { i, anchor ->
                            val bounds = anchorBoundsList[i]
                            val paddingStart = if (bounds.isLeftEdge) 20.dp else 8.dp
                            val paddingEnd = if (bounds.isRightEdge) 20.dp else 8.dp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = paddingStart,
                                        end = paddingEnd,
                                        top = 8.dp,
                                        bottom = 8.dp
                                    )
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
                    val anchorPlacements = anchorMeasurables.mapIndexed { i, measurable ->
                        val cellWidthPx = anchorBoundsList[i].widthPx.roundToInt().coerceAtLeast(0)
                        measurable.measure(
                            constraints.copy(
                                minWidth = cellWidthPx,
                                maxWidth = cellWidthPx,
                                minHeight = 0,
                                maxHeight = Constraints.Infinity
                            )
                        )
                    }

                    // 3. Expand endRow heights if any rowspan anchor is taller than its spanned rows
                    val originalRowHeights = rowHeights.copyOf()
                    anchors.forEachIndexed { i, anchor ->
                        var currentSpanHeight = 0
                        for (r in anchor.startRow..anchor.endRow) {
                            currentSpanHeight += rowHeights[r]
                            if (r < anchor.endRow) {
                                currentSpanHeight += dividerAfterHeights[r]
                            }
                        }
                        val needed = anchorPlacements[i].height
                        if (needed > currentSpanHeight) {
                            rowHeights[anchor.endRow] += (needed - currentSpanHeight)
                        }
                    }

                    val rowPositions = IntArray(renderModel.rows.size)
                    val dividerPositions = IntArray(renderModel.rows.size)
                    var runningY = 0
                    for (r in renderModel.rows.indices) {
                        rowPositions[r] = runningY
                        runningY += rowHeights[r]
                        if (r < renderModel.rows.lastIndex) {
                            dividerPositions[r] = runningY
                            runningY += dividerAfterHeights[r]
                        }
                    }
                    val totalHeight = runningY

                    // 4. Fill expanded row backgrounds if any endRow grew
                    val expandedRows = renderModel.rows.indices.filter { rowHeights[it] > originalRowHeights[it] }
                    val fillerMeasurables = subcompose("row_fillers") {
                        expandedRows.forEach { rowIdx ->
                            val rowModel = renderModel.rows[rowIdx]
                            val rowBg = resolveRowBaseBackground(
                                row = rowModel,
                                tableClassNames = tableClassNames
                            )
                            Box(modifier = Modifier.background(rowBg))
                        }
                    }
                    val fillerPlacements = fillerMeasurables.mapIndexed { idx, measurable ->
                        val rowIdx = expandedRows[idx]
                        measurable.measure(
                            Constraints.fixed(
                                width = constraints.maxWidth,
                                height = rowHeights[rowIdx]
                            )
                        )
                    }

                    // 5. Subcompose stateless anchor backgrounds matching exact spanHeight
                    val anchorBackgroundMeasurables = subcompose("anchor_backgrounds") {
                        anchors.forEachIndexed { i, anchor ->
                            val startRowModel = renderModel.rows[anchor.startRow]
                            val bounds = anchorBoundsList[i]
                            val baseBackground = resolveRowBaseBackground(
                                row = startRowModel,
                                tableClassNames = tableClassNames
                            )
                            val cellBackground = resolveCellBackgroundColor(
                                cellClassNames = anchor.cell.cell.classNames,
                                showSelectedHighlight = showSelectedHighlight,
                                defaultColor = Color.Transparent,
                            )
                            Box(modifier = Modifier.background(baseBackground)) {
                                if (cellBackground != Color.Transparent) {
                                    Surface(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .padding(
                                                start = if (bounds.isLeftEdge) 16.dp else 4.dp,
                                                end = if (bounds.isRightEdge) 16.dp else 4.dp,
                                                top = 8.dp,
                                                bottom = 8.dp
                                            ),
                                        color = cellBackground,
                                        shape = MaterialTheme.shapes.extraSmall,
                                        content = {}
                                    )
                                }
                            }
                        }
                    }
                    val anchorBackgroundPlacements = anchorBackgroundMeasurables.mapIndexed { i, measurable ->
                        val anchor = anchors[i]
                        val cellWidthPx = anchorBoundsList[i].widthPx.roundToInt().coerceAtLeast(0)
                        val spanHeight = (rowPositions[anchor.endRow] + rowHeights[anchor.endRow] - rowPositions[anchor.startRow])
                            .coerceAtLeast(0)
                        measurable.measure(Constraints.fixed(width = cellWidthPx, height = spanHeight))
                    }

                    layout(constraints.maxWidth, totalHeight) {
                        fillerPlacements.forEachIndexed { idx, placement ->
                            val rowIdx = expandedRows[idx]
                            placement.place(0, rowPositions[rowIdx])
                        }
                        layoutItems.forEachIndexed { itemIndex, item ->
                            val placement = rowPlacements[itemIndex]
                            val y = when (item) {
                                is TableLayoutItem.RowItem -> rowPositions[item.rowIndex]
                                is TableLayoutItem.DividerItem -> dividerPositions[item.afterRowIndex]
                            }
                            placement.place(0, y)
                        }
                        anchors.forEachIndexed { i, anchor ->
                            val startY = rowPositions[anchor.startRow]
                            val spanHeight = rowPositions[anchor.endRow] + rowHeights[anchor.endRow] - startY
                            val leftX = anchorBoundsList[i].leftX.roundToInt()
                            anchorBackgroundPlacements[i].place(leftX, startY)
                            val centeredY = startY + ((spanHeight - anchorPlacements[i].height) / 2).coerceAtLeast(0)
                            anchorPlacements[i].place(leftX, centeredY)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun resolveRowBaseBackground(
    row: TableRenderedRow,
    tableClassNames: Set<String>,
): Color {
    val isAbstractRow = (row.classNames + tableClassNames).containsInsensitive("abstract")
    return when {
        row.isHeaderRow -> MaterialTheme.colorScheme.surfaceContainerHighest
        isAbstractRow -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }
}

private data class AnchorHorizontalBounds(
    val leftX: Float,
    val widthPx: Float,
    val isLeftEdge: Boolean,
    val isRightEdge: Boolean,
)

private fun computeAnchorHorizontalBounds(
    row: TableRenderedRow,
    cellIndex: Int,
    tableWidthPx: Float,
    insetSmPx: Float,
    columnWeights: List<Float>,
): AnchorHorizontalBounds {
    val totalColumnCount = columnWeights.size
    val weights = resolveRowCellWeights(row, columnWeights)
    val rowSpanSum = row.cells.sumOf { it.columnSpan.coerceAtLeast(1) }
    val trailingCols = (rowSpanSum until totalColumnCount).sumOf { columnWeights[it].toDouble() }.toFloat()
    val totalWeight = (weights.sum() + trailingCols).coerceAtLeast(1f)
    val startWeight = weights.take(cellIndex).sum()
    val cellWeight = weights.getOrElse(cellIndex) { 1f }

    val usableWidth = (tableWidthPx - insetSmPx * 2).coerceAtLeast(0f)
    val isLeftEdge = cellIndex == 0
    val isRightEdge = cellIndex == row.cells.lastIndex && rowSpanSum >= totalColumnCount

    val leftX = if (isLeftEdge) 0f else insetSmPx + usableWidth * (startWeight / totalWeight)
    val rightX = if (isRightEdge) tableWidthPx else insetSmPx + usableWidth * ((startWeight + cellWeight) / totalWeight)

    return AnchorHorizontalBounds(
        leftX = leftX,
        widthPx = (rightX - leftX).coerceAtLeast(0f),
        isLeftEdge = isLeftEdge,
        isRightEdge = isRightEdge,
    )
}

private fun resolveRowCellWeights(
    row: TableRenderedRow,
    columnWeights: List<Float>,
): List<Float> {
    var col = 0
    return row.cells.map { cell ->
        val span = cell.columnSpan.coerceAtLeast(1)
        var weight = 0f
        for (i in 0 until span) {
            weight += columnWeights.getOrElse(col + i) { 1f }
        }
        col += span
        weight.coerceAtLeast(1f)
    }
}

private class RowspanAnchorInfo(
    val cell: TableRenderedCell,
    val cellIndex: Int,
    val startRow: Int,
    val endRow: Int
)

/**
 * Represents a table prepared for rendering with processed rowspan/colspan.
 */
internal data class TableRenderModel(
    val rows: List<TableRenderedRow>,
    val columnCount: Int,
    val columnWeights: List<Float> = List(columnCount) { 1f },
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
    val totalRows = rows.size
    val renderedRows = rows.mapIndexed { index, row ->
        val remainingRows = totalRows - index
        builder.renderRow(row, remainingRows)
    }
    val resolvedColumns = maxOf(columnCount, builder.columnCount)
        .coerceAtMost(RichTextParserConfig.MAX_TABLE_COLUMNS)
    val columnWeights = resolveTableColumnWeights(renderedRows, resolvedColumns)
    return TableRenderModel(
        rows = renderedRows,
        columnCount = resolvedColumns,
        columnWeights = columnWeights,
    )
}

private fun resolveTableColumnWeights(
    renderedRows: List<TableRenderedRow>,
    columnCount: Int,
): List<Float> {
    if (columnCount <= 0) return emptyList()
    val discoveredWidths = FloatArray(columnCount)
    for (row in renderedRows) {
        var col = 0
        for (cell in row.cells) {
            val span = cell.columnSpan.coerceAtLeast(1)
            val width = cell.cell.width
            if (cell.isVisible && span == 1 && width != null && width > 0f && col < columnCount && discoveredWidths[col] == 0f) {
                discoveredWidths[col] = width
            }
            col += span
        }
    }
    val allColumnsHaveExplicitWidth = discoveredWidths.all { it > 0f }
    return if (allColumnsHaveExplicitWidth) {
        discoveredWidths.toList()
    } else {
        List(columnCount) { 1f }
    }
}

private class TableGridBuilder {
    private val spanSlots = mutableListOf<ColumnSpan?>()
    var columnCount: Int = 0
        private set

    fun renderRow(row: RichTextTableRow, remainingRows: Int = Int.MAX_VALUE): TableRenderedRow {
        val pendingCells = ArrayDeque(row.cells)
        val renderedCells = mutableListOf<TableRenderedCell>()
        var columnIndex = 0
        val maxIterations = MAX_COLUMN_ITERATIONS
        var iterations = 0
        while ((pendingCells.isNotEmpty() || hasAnchorsFrom(columnIndex)) &&
            columnIndex < RichTextParserConfig.MAX_TABLE_COLUMNS &&
            iterations < maxIterations
        ) {
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
                    if (pendingCells.isEmpty()) {
                        renderedCells += TableRenderedCell(
                            cell = RichTextTableCell(text = AnnotatedString("")),
                            columnSpan = 1,
                            rowSpan = 1,
                            isVisible = false,
                            isRowspanEnd = true
                        )
                        columnIndex += 1
                        continue
                    }
                    val rawCell = pendingCells.removeFirst()
                    val effectiveRowSpan = rawCell.rowSpan.coerceIn(1, remainingRows.coerceAtLeast(1))
                    val cell = if (effectiveRowSpan != rawCell.rowSpan) rawCell.copy(rowSpan = effectiveRowSpan) else rawCell
                    val maxRemainingCols = (RichTextParserConfig.MAX_TABLE_COLUMNS - columnIndex).coerceAtLeast(1)
                    val spanWidth = cell.columnSpan.coerceIn(1, maxRemainingCols)
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
        columnCount = max(columnCount, columnIndex).coerceAtMost(RichTextParserConfig.MAX_TABLE_COLUMNS)
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
 * Renders a single table row with proper styling based on header status and classes.
 */
@Composable
internal fun TableRowContent(
    row: TableRenderedRow,
    tableClassNames: Set<String>,
    columnWeights: List<Float>,
    showSelectedHighlight: Boolean = false,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
    isRowspanOverlayEnabled: Boolean = false,
    customCellContent: (@Composable (cell: TableRenderedCell, textStyle: TextStyle, textColor: Color, cellIndex: Int) -> Unit)? = null
) {
    val totalColumnCount = columnWeights.size
    val effectiveRowClasses = row.classNames + tableClassNames
    val isAbstractRow = effectiveRowClasses.containsInsensitive("abstract")
    val baseBackground = resolveRowBaseBackground(row = row, tableClassNames = tableClassNames)
    val weights = resolveRowCellWeights(row = row, columnWeights = columnWeights)
    val rowSpanSum = row.cells.sumOf { it.columnSpan.coerceAtLeast(1) }
    val trailingWeight = (rowSpanSum until totalColumnCount).sumOf { columnWeights[it].toDouble() }.toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        row.cells.forEachIndexed { cellIndex, cell ->
            val weight = weights[cellIndex]
            if (!cell.isVisible || (isRowspanOverlayEnabled && cell.rowSpan > 1)) {
                Spacer(modifier = Modifier.weight(weight))
            } else {
                val isHeaderCell = row.isHeaderRow || cell.cell.isHeader
                val textStyle = tableCellTextStyle(isHeaderCell, cell.cell.classNames)
                val textColor = resolveCellTextColor(
                    isHeaderCell = isHeaderCell,
                    isAbstractRow = isAbstractRow,
                    cellClassNames = cell.cell.classNames,
                    showSelectedHighlight = showSelectedHighlight,
                )
                val cellBackground = resolveCellBackgroundColor(
                    cellClassNames = cell.cell.classNames,
                    showSelectedHighlight = showSelectedHighlight,
                )
                Surface(
                    modifier = Modifier
                        .weight(weight)
                        .padding(horizontal = 4.dp),
                    color = cellBackground,
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
                        if (customCellContent != null) {
                            customCellContent(cell, textStyle, textColor, cellIndex)
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
        if (trailingWeight > 0f) {
            Spacer(modifier = Modifier.weight(trailingWeight))
        }
    }
}
