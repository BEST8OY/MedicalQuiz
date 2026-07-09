package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.max
import androidx.compose.ui.text.TextStyle
import kotlin.collections.ArrayDeque
import kotlin.collections.buildList
import kotlin.math.max
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.ui.theme.Stroke

/** Maximum iterations per row to prevent infinite loops from malformed HTML */
private const val MAX_COLUMN_ITERATIONS = 500

/**
 * Shared layout shell for tables: a bordered [Surface] containing a horizontally
 * scrolling column of rows separated by dividers. Both [RichTextTable] and
 * [HighlightableTable] route through this so layout cannot drift between them.
 *
 * @param block The table block to render
 * @param renderRow Renders a single row
 */
@Composable
internal fun RichTextTableShell(
    block: RichTextBlock.Table,
    renderRow: @Composable (row: TableRenderedRow) -> Unit
) {
    val renderModel = remember(block) { block.toRenderModel() }
    if (renderModel.columnCount == 0) return
    val scrollState = rememberScrollState()
    val minTableWidth = Layout.TableMinCellWidth * renderModel.columnCount

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(Stroke.Thin, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints {
            val tableWidth = max(minTableWidth, maxWidth)
            Column(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .width(tableWidth)
            ) {
                renderModel.rows.forEachIndexed { index, row ->
                    renderRow(row)
                    if (index != renderModel.rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

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
    RichTextTableShell(block) { row ->
        TableRowContent(
            row = row,
            tableClassNames = block.classNames,
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick
        )
    }
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
    val isVisible: Boolean
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
                    renderedCells += TableRenderedCell(
                        cell = tracker.cell,
                        columnSpan = tracker.spanWidth,
                        isVisible = false
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
                        isVisible = true
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
    customCellContent: (@Composable (cell: TableRenderedCell, textStyle: TextStyle, cellIndex: Int) -> Unit)? = null
) {
    val effectiveRowClasses = row.classNames + tableClassNames
    val isAbstractRow = effectiveRowClasses.containsInsensitive("abstract")
    val baseBackground = when {
        row.isHeaderRow -> MaterialTheme.colorScheme.surfaceContainerHighest
        isAbstractRow -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(baseBackground)
            .padding(horizontal = Inset.Sm, vertical = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        row.cells.forEachIndexed { cellIndex, cell ->
            val weight = cell.cell.width ?: cell.columnSpan.coerceAtLeast(1).toFloat()
            if (!cell.isVisible) {
                Spacer(modifier = Modifier.weight(weight))
            } else {
                val isHeaderCell = row.isHeaderRow || cell.cell.isHeader
                val textStyle = tableCellTextStyle(isHeaderCell)
                val textColor = when {
                    cell.cell.classNames.containsInsensitive("selected") -> MaterialTheme.colorScheme.onSecondaryContainer
                    cell.cell.classNames.containsInsensitive("wichtig") -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> tableCellTextColor(
                        isHeaderCell = isHeaderCell,
                        isAbstractClass = isAbstractRow || cell.cell.classNames.containsInsensitive("abstract")
                    )
                }
                val cellBackground = when {
                    cell.cell.classNames.containsInsensitive("selected") -> MaterialTheme.colorScheme.secondaryContainer
                    cell.cell.classNames.containsInsensitive("wichtig") -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> Color.Transparent
                }
                Surface(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .padding(horizontal = Spacing.Xxs),
                    color = cellBackground,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Xxs)
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
