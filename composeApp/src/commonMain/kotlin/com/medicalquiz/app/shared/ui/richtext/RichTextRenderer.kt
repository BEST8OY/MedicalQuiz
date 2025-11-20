package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.medicalquiz.app.shared.utils.HtmlUtils
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.max

sealed interface RichTextBlock {
    data class Paragraph(val text: AnnotatedString, val textAlign: TextAlign = TextAlign.Start) : RichTextBlock
    data class Heading(val level: Int, val text: AnnotatedString, val textAlign: TextAlign = TextAlign.Start) : RichTextBlock
    data class BulletList(val items: List<AnnotatedString>) : RichTextBlock
    data class OrderedList(val items: List<AnnotatedString>, val start: Int) : RichTextBlock
    data class CodeBlock(val text: String) : RichTextBlock
    data class Table(
        val headerRows: List<RichTextTableRow>,
        val bodyRows: List<RichTextTableRow>,
        val columnCount: Int,
        val classNames: Set<String> = emptySet()
    ) : RichTextBlock
    data class AbstractBlock(
        val title: AnnotatedString?,
        val blocks: List<RichTextBlock>,
        val classNames: Set<String> = emptySet()
    ) : RichTextBlock
    data class Media(
        val source: String,
        val mediaRef: String?,
        val description: String?,
        val width: Int?,
        val height: Int?,
        val alignment: TextAlign,
        val classNames: Set<String> = emptySet()
    ) : RichTextBlock
    data object Divider : RichTextBlock
}

data class RichTextTableRow(
    val cells: List<RichTextTableCell>,
    val isHeader: Boolean,
    val classNames: Set<String> = emptySet()
)

data class RichTextTableCell(
    val text: AnnotatedString,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val alignment: TextAlign = TextAlign.Start,
    val isHeader: Boolean = false,
    val classNames: Set<String> = emptySet(),
    val width: Float? = null
)

@Immutable
data class RichTextPalette(
    val importantBackground: Color,
    val importantText: Color,
    val selectedBackground: Color,
    val selectedText: Color,
    val dictionaryText: Color,
    val abstractText: Color
)

private data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val monospace: Boolean = false,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
    val link: String? = null,
    val highlight: InlineHighlight? = null,
    val dictionary: Boolean = false,
    val preserveWhitespace: Boolean = false,
    val smallText: Boolean = false,
    val textColor: Color? = null,
    val tooltip: String? = null
)

private enum class InlineHighlight { IMPORTANT, SELECTED }

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
        blocks.forEach { block ->
            RichTextBlockRenderer(
                block = block,
                onLinkClick = resolvedLinkHandler,
                onMediaClick = resolvedMediaHandler,
                onTooltipClick = resolvedTooltipHandler
            )
        }
    }
    tooltipMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { tooltipMessage = null },
            confirmButton = {
                TextButton(onClick = { tooltipMessage = null }) {
                    androidx.compose.material3.Text(text = "Close")
                }
            },
            text = {
                androidx.compose.material3.Text(text = message)
            },
            title = {
                androidx.compose.material3.Text(text = "Description")
            }
        )
    }
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
private fun rememberLinkHandler(onLinkClick: ((String) -> Unit)?): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(onLinkClick, uriHandler) {
        onLinkClick ?: { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: Throwable) {
                // Ignore failures opening deep links.
            }
        }
    }
}

@Composable
private fun rememberMediaHandler(onMediaClick: ((String) -> Unit)?): (String) -> Unit {
    return remember(onMediaClick) { onMediaClick ?: {} }
}

@Composable
private fun RichTextParagraph(
    text: AnnotatedString,
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    InteractiveText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
            textIndent = TextIndent.None
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick
    )
}

@Composable
private fun InteractiveText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    textAlign: TextAlign? = null,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Visible
) {
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val hasInteractiveAnnotations = remember(text) {
        text.getStringAnnotations("URL", 0, text.length).isNotEmpty() ||
            text.getStringAnnotations("TOOLTIP", 0, text.length).isNotEmpty()
    }
    val pointerModifier = if (hasInteractiveAnnotations) {
        Modifier.pointerInput(text, layoutResult) {
            detectTapGestures { position ->
                val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                text.getStringAnnotations("TOOLTIP", offset, offset).firstOrNull()?.let {
                    onTooltipClick?.invoke(it.item)
                    return@detectTapGestures
                }
                text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                    if (it.item.isNotBlank()) {
                        onLinkClick(it.item)
                    }
                }
            }
        }
    } else {
        Modifier
    }

    androidx.compose.material3.Text(
        text = text,
        modifier = modifier.then(pointerModifier),
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult = it }
    )
}

@Composable
private fun RichTextBlockRenderer(
    block: RichTextBlock,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    when (block) {
        is RichTextBlock.Paragraph -> RichTextParagraph(block.text, textAlign = block.textAlign, onLinkClick = onLinkClick, onTooltipClick = onTooltipClick)
        is RichTextBlock.Heading -> RichTextHeading(block, onLinkClick, onTooltipClick)
        is RichTextBlock.BulletList -> RichTextList(
            items = block.items,
            markerProvider = { _ -> "\u2022" },
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick
        )
        is RichTextBlock.OrderedList -> RichTextList(
            items = block.items,
            markerProvider = { index -> "${block.start + index}." },
            onLinkClick = onLinkClick,
            onTooltipClick = onTooltipClick
        )
        is RichTextBlock.CodeBlock -> RichTextCodeBlock(block)
        is RichTextBlock.Table -> RichTextTable(block, onLinkClick, onTooltipClick)
        is RichTextBlock.AbstractBlock -> AbstractCard(block, onLinkClick, onTooltipClick)
        is RichTextBlock.Media -> RichMedia(block = block, onMediaClick = onMediaClick)
        RichTextBlock.Divider -> HorizontalDivider()
    }
}

@Composable
private fun RichTextHeading(
    block: RichTextBlock.Heading,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        4 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    InteractiveText(
        text = block.text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = block.textAlign,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Visible
    )
}

@Composable
private fun RichTextList(
    items: List<AnnotatedString>,
    markerProvider: (index: Int) -> String,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Text(
                    text = markerProvider(index),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 12.dp)
                )
                RichTextParagraph(
                    text = item,
                    modifier = Modifier.weight(1f),
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick
                )
            }
        }
    }
}

@Composable
private fun RichTextCodeBlock(block: RichTextBlock.CodeBlock) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        androidx.compose.material3.Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun RichTextTable(
    block: RichTextBlock.Table,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    if (block.columnCount == 0) return
    val renderModel = remember(block) { block.toRenderModel() }
    val scrollState = rememberScrollState()
    val minWidth = 120.dp * renderModel.columnCount
    BoxWithConstraints {
        val needsScroll = minWidth > maxWidth
        val tableModifier = if (needsScroll) {
            Modifier
                .horizontalScroll(scrollState)
                .width(minWidth)
        } else {
            Modifier.fillMaxWidth()
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = tableModifier) {
                renderModel.rows.forEachIndexed { index, row ->
                    TableRowContent(
                        row = row,
                        tableClassNames = block.classNames,
                        onLinkClick = onLinkClick,
                        onTooltipClick = onTooltipClick
                    )
                    if (index != renderModel.rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

private data class TableRenderModel(
    val rows: List<TableRenderedRow>,
    val columnCount: Int
)

private data class TableRenderedRow(
    val cells: List<TableRenderedCell>,
    val isHeaderRow: Boolean,
    val classNames: Set<String>
)

private data class TableRenderedCell(
    val cell: RichTextTableCell,
    val columnSpan: Int,
    val isVisible: Boolean
)

private fun RichTextBlock.Table.toRenderModel(): TableRenderModel {
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
        while (pendingCells.isNotEmpty() || hasAnchorsFrom(columnIndex)) {
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

@Composable
private fun RichMedia(block: RichTextBlock.Media, onMediaClick: (String) -> Unit) {
    val mediaModel = remember(block.source, block.mediaRef) {
        mediaModelForSource(block.source, block.mediaRef)
    }
    if (mediaModel == null) return
    val clickTarget = block.mediaRef ?: block.source
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = when (block.alignment) {
            TextAlign.End -> Alignment.End
            TextAlign.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        AsyncImage(
            model = mediaModel,
            contentDescription = block.description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMediaClick(clickTarget) }
        )
        block.description?.let {
            androidx.compose.material3.Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = block.alignment
            )
        }
    }
}

@Composable
private fun TableRowContent(
    row: TableRenderedRow,
    tableClassNames: Set<String>,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    val effectiveRowClasses = row.classNames + tableClassNames
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
        verticalAlignment = Alignment.Top
    ) {
        row.cells.forEach { cell ->
            val weight = cell.cell.width ?: cell.columnSpan.coerceAtLeast(1).toFloat()
            if (!cell.isVisible) {
                Spacer(modifier = Modifier.weight(weight))
            } else {
                val isHeaderCell = row.isHeaderRow || cell.cell.isHeader
                val textStyle = if (isHeaderCell) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
                val textColor = when {
                    isHeaderCell -> MaterialTheme.colorScheme.onSecondaryContainer
                    cell.cell.classNames.containsInsensitive("abstract") -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                }
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
                    InteractiveText(
                        text = cell.cell.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
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

private fun Set<String>.containsInsensitive(target: String): Boolean {
    return any { it.equals(target, ignoreCase = true) }
}

private fun Set<String>.containsAnyInsensitive(targets: Set<String>): Boolean {
    if (isEmpty() || targets.isEmpty()) return false
    return targets.any { target -> containsInsensitive(target) }
}

@Composable
private fun AbstractCard(
    block: RichTextBlock.AbstractBlock,
    onLinkClick: (String) -> Unit,
    onTooltipClick: ((String) -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.title?.let {
                androidx.compose.material3.Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (block.blocks.isNotEmpty()) {
                RichText(
                    blocks = block.blocks,
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick
                )
            }
        }
    }
}

@Composable
private fun rememberRichTextPalette(): RichTextPalette {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        RichTextPalette(
            importantBackground = colors.tertiaryContainer,
            importantText = colors.onTertiaryContainer,
            selectedBackground = colors.primaryContainer,
            selectedText = colors.onPrimaryContainer,
            dictionaryText = colors.primary,
            abstractText = colors.onSurfaceVariant
        )
    }
}

// -----------------------------------------------------------------------------
// Ksoup Parser Implementation
// -----------------------------------------------------------------------------

private object RichTextParser {

    fun parse(
        html: String,
        palette: RichTextPalette,
        showSelectedHighlight: Boolean
    ): List<RichTextBlock> {
        // Ksoup parser usage
        val handler = RichTextHandler(palette, showSelectedHighlight)
        val parser = KsoupHtmlParser(handler = handler)
        parser.write(html)
        parser.end()
        return handler.blocks
    }
}

private sealed interface KsoupNode {
    val parent: KsoupElement?
}

private class KsoupTextNode(val text: String, override val parent: KsoupElement?) : KsoupNode

private class KsoupElement(
    val tagName: String,
    val attributes: Map<String, String>,
    override val parent: KsoupElement?
) : KsoupNode {
    val children = mutableListOf<KsoupNode>()

    fun attr(name: String): String = attributes[name] ?: ""
    fun hasAttr(name: String): Boolean = attributes.containsKey(name)
    fun classNames(): Set<String> = attributes["class"]?.split(" ")?.toSet() ?: emptySet()

    fun text(): String {
        val sb = StringBuilder()
        collectText(this, sb)
        return sb.toString()
    }

    private fun collectText(node: KsoupNode, sb: StringBuilder) {
        when (node) {
            is KsoupTextNode -> sb.append(node.text)
            is KsoupElement -> node.children.forEach { collectText(it, sb) }
        }
    }
}

private fun KsoupElement.collectAncestorClasses(stopAt: KsoupElement?): Set<String> {
    val classes = mutableSetOf<String>()
    var cursor = parent
    while (cursor != null && cursor != stopAt) {
        cursor.classNames()
            .filter { it.isNotBlank() }
            .forEach { classes += it }
        cursor = cursor.parent
    }
    return classes
}

private class RichTextHandler(
    private val palette: RichTextPalette,
    private val showSelectedHighlight: Boolean
) : KsoupHtmlHandler {

    val blocks = mutableListOf<RichTextBlock>()
    private var currentElement: KsoupElement? = null
    private val rootElements = mutableListOf<KsoupNode>()

    override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
        val newElement = KsoupElement(name, attributes, currentElement)
        if (currentElement == null) {
            rootElements.add(newElement)
        } else {
            currentElement?.children?.add(newElement)
        }
        currentElement = newElement
    }

    override fun onText(text: String) {
        if (text.isEmpty()) return
        val textNode = KsoupTextNode(text, currentElement)
        if (currentElement == null) {
            rootElements.add(textNode)
        } else {
            currentElement?.children?.add(textNode)
        }
    }

    override fun onCloseTag(name: String, isImplied: Boolean) {
        if (currentElement?.tagName == name) {
            currentElement = currentElement?.parent
        }
    }

    override fun onEnd() {
        val domParser = RichTextDomParser(palette, showSelectedHighlight)
        blocks.addAll(domParser.parse(rootElements))
    }
}

private class RichTextDomParser(
    private val palette: RichTextPalette,
    private val showSelectedHighlight: Boolean
) {

    data class InheritedStyles(val textAlign: TextAlign? = null)
    private val ignoredTagNames = setOf("style", "script", "head", "meta", "link", "title")

    fun parse(
        nodes: List<KsoupNode>,
        inheritedStyles: InheritedStyles = InheritedStyles()
    ): List<RichTextBlock> {
        val blocks = mutableListOf<RichTextBlock>()
        nodes.forEach { node ->
            when (node) {
                is KsoupTextNode -> {
                    val text = node.text.trim()
                    if (text.isNotEmpty()) {
                        blocks += RichTextBlock.Paragraph(
                            text = buildAnnotatedString { append(text) },
                            textAlign = inheritedStyles.textAlign ?: TextAlign.Start
                        )
                    }
                }
                is KsoupElement -> {
                    val tag = node.tagName.lowercase()
                    if (ignoredTagNames.contains(tag)) return@forEach
                    val elementTextAlign = parseTextAlign(node)
                    val currentTextAlign = elementTextAlign ?: inheritedStyles.textAlign
                    val nextStyles = inheritedStyles.copy(textAlign = currentTextAlign)

                    when (tag) {
                        "p" -> handleParagraph(node, blocks, nextStyles)
                        "h1", "h2", "h3", "h4", "h5", "h6" -> {
                            val level = tag.removePrefix("h").toIntOrNull() ?: 6
                            buildAnnotatedBlock(node)?.let { heading ->
                                if (heading.text.isNotBlank()) {
                                    blocks += RichTextBlock.Heading(
                                        level = level,
                                        text = heading,
                                        textAlign = currentTextAlign ?: TextAlign.Start
                                    )
                                }
                            }
                        }
                        "ul" -> {
                            val items = node.children
                                .mapNotNull { child ->
                                    if (child is KsoupElement && child.tagName.equals("li", ignoreCase = true)) buildAnnotatedBlock(child)
                                    else null
                                }
                                .filter { it.text.isNotBlank() }
                            if (items.isNotEmpty()) blocks += RichTextBlock.BulletList(items)
                        }
                        "ol" -> {
                            val start = node.attr("start").toIntOrNull() ?: 1
                            val items = node.children
                                .mapNotNull { child ->
                                    if (child is KsoupElement && child.tagName.equals("li", ignoreCase = true)) buildAnnotatedBlock(child)
                                    else null
                                }
                                .filter { it.text.isNotBlank() }
                            if (items.isNotEmpty()) blocks += RichTextBlock.OrderedList(items, start)
                        }
                        "hr" -> blocks += RichTextBlock.Divider
                        "pre", "code" -> {
                            val codeText = node.text().trim()
                            if (codeText.isNotEmpty()) blocks += RichTextBlock.CodeBlock(codeText)
                        }
                        "table" -> parseTable(node)?.let(blocks::add)
                        "div", "section", "article" -> {
                            if (node.classNames().any { it.equals("abstract", ignoreCase = true) }) {
                                parseAbstractBlock(node)?.let(blocks::add)
                            } else {
                                blocks += parse(node.children, nextStyles)
                            }
                        }
                        "img" -> parseMediaElement(node, currentTextAlign)?.let(blocks::add)
                        else -> {
                            buildAnnotatedBlock(node)?.let { paragraph ->
                                if (paragraph.text.isNotBlank()) {
                                    blocks += RichTextBlock.Paragraph(
                                        text = paragraph,
                                        textAlign = currentTextAlign ?: TextAlign.Start
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return blocks
    }

    private fun parseTextAlign(element: KsoupElement): TextAlign? {
        val align = element.attr("align").lowercase()
        if (align.isNotEmpty()) {
            return when (align) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                "justify" -> TextAlign.Justify
                else -> TextAlign.Start
            }
        }
        val style = element.attr("style").lowercase()
        if (style.contains("text-align")) {
            val value = style.substringAfter("text-align").substringAfter(":").substringBefore(";").trim()
            return when (value) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                "justify" -> TextAlign.Justify
                else -> TextAlign.Start
            }
        }
        return null
    }

    private fun handleParagraph(
        node: KsoupElement,
        blocks: MutableList<RichTextBlock>,
        inheritedStyles: InheritedStyles
    ) {
        val paragraphNodes = mutableListOf<KsoupNode>()
        val mediaElements = mutableListOf<KsoupElement>()
        val paragraphAlignment = parseTextAlign(node) ?: inheritedStyles.textAlign

        node.children.forEach { child ->
            if (child is KsoupElement && child.tagName.equals("img", ignoreCase = true)) {
                mediaElements.add(child)
            } else {
                paragraphNodes.add(child)
            }
        }

        val builder = buildAnnotatedString {
            val baseStyle = InlineStyle().applyClassStyles(node.classNames(), palette, showSelectedHighlight)
            appendNodes(paragraphNodes, baseStyle)
        }
        if (builder.text.isNotBlank()) {
            blocks += RichTextBlock.Paragraph(
                text = builder,
                textAlign = paragraphAlignment ?: TextAlign.Start
            )
        }

        mediaElements.forEach { image ->
            parseMediaElement(image, paragraphAlignment)?.let(blocks::add)
        }
    }

    private fun buildAnnotatedBlock(element: KsoupElement): AnnotatedString? {
        val baseStyle = InlineStyle().applyClassStyles(element.classNames(), palette, showSelectedHighlight)
        val builder = buildAnnotatedString {
            appendNodes(element.children, baseStyle)
        }
        return builder.takeIf { it.text.isNotBlank() }
    }

    private fun AnnotatedString.Builder.appendNodes(
        nodes: List<KsoupNode>,
        style: InlineStyle
    ) {
        nodes.forEach { node -> appendNode(node, style) }
    }

    private fun AnnotatedString.Builder.appendNode(
        node: KsoupNode,
        style: InlineStyle
    ) {
        when (node) {
            is KsoupTextNode -> {
                val text = node.text.replace('\u00A0', ' ')
                when {
                    text.isEmpty() -> Unit
                    text.isBlank() -> appendTextWithStyle(" ", style, palette)
                    else -> appendTextWithStyle(text, style, palette)
                }
            }
            is KsoupElement -> {
                if (node.isTooltipContentNode()) return
                val tag = node.tagName.lowercase()
                if (ignoredTagNames.contains(tag)) return

                if (tag == "br") {
                    append("\n")
                    return
                }

                if (tag == "li") {
                    append("\n• ")
                }

                var nextStyle = when (tag) {
                    "strong", "b" -> style.copy(bold = true)
                    "em", "i" -> style.copy(italic = true)
                    "u" -> style.copy(underline = true)
                    "code" -> style.copy(monospace = true)
                    "sup" -> style.copy(superscript = true)
                    "sub" -> style.copy(subscript = true)
                    "a" -> {
                        val href = node.attr("href").trim().takeUnless { hrefValue ->
                            hrefValue.isEmpty() || hrefValue == "#" || hrefValue.startsWith("javascript", ignoreCase = true)
                        }
                        if (href != null) style.copy(link = href) else style
                    }
                    else -> style
                }
                nextStyle = nextStyle.applyClassStyles(node.classNames(), palette, showSelectedHighlight)
                extractTooltipText(node)?.let { tooltip ->
                    nextStyle = nextStyle.copy(tooltip = tooltip)
                }
                appendNodes(node.children, nextStyle)

                if (tag == "p" || tag == "div") {
                    append("\n")
                }
            }
        }
    }

    private fun parseTable(element: KsoupElement): RichTextBlock.Table? {
        val allRows = mutableListOf<KsoupElement>()

        fun collectRows(el: KsoupElement) {
            if (el.tagName.equals("tr", ignoreCase = true)) {
                allRows.add(el)
            } else {
                el.children.filterIsInstance<KsoupElement>().forEach { collectRows(it) }
            }
        }
        collectRows(element)

        if (allRows.isEmpty()) return null

        val headerRows = mutableListOf<RichTextTableRow>()
        val bodyRows = mutableListOf<RichTextTableRow>()

        allRows.forEach { tr ->
            var isHeaderContext = false
            var parent = tr.parent
            while (parent != null && parent != element) {
                if (parent.tagName.equals("thead", ignoreCase = true)) {
                    isHeaderContext = true
                    break
                }
                parent = parent.parent
            }

            val parsedRow = parseTableRow(tr, element, isHeaderContext)
            if (parsedRow.isHeader) {
                headerRows.add(parsedRow)
            } else {
                bodyRows.add(parsedRow)
            }
        }

        val columnCount = (headerRows + bodyRows)
            .maxOfOrNull { row ->
                row.cells.sumOf { cell -> cell.columnSpan.coerceAtLeast(1) }
            } ?: 0
        if (columnCount == 0) return null

        return RichTextBlock.Table(
            headerRows = headerRows,
            bodyRows = bodyRows,
            columnCount = columnCount,
            classNames = element.classNames()
        )
    }

    private fun parseTableRow(
        row: KsoupElement,
        tableElement: KsoupElement,
        headerContext: Boolean
    ): RichTextTableRow {
        val cellElements = mutableListOf<KsoupElement>()

        fun collectCells(el: KsoupElement) {
            el.children.filterIsInstance<KsoupElement>().forEach { child ->
                val tag = child.tagName.lowercase()
                if (tag == "td" || tag == "th") {
                    cellElements.add(child)
                } else if (tag != "table") {
                    collectCells(child)
                }
            }
        }
        collectCells(row)

        val isHeaderRow = headerContext || cellElements.all { it.tagName.equals("th", ignoreCase = true) }
        if (cellElements.isEmpty()) {
            return RichTextTableRow(emptyList(), isHeaderRow, buildRowClassSet(row, tableElement))
        }
        val cells = cellElements.map { cell ->
            val text = buildAnnotatedBlock(cell) ?: AnnotatedString("")
            val columnSpan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val rowSpan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val alignment = when (cell.attr("align").lowercase()) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                else -> TextAlign.Start
            }
            val width = parseWidth(cell.attr("width"), cell.attr("style"))
            val isHeaderCell = headerContext || cell.tagName.equals("th", ignoreCase = true)
            RichTextTableCell(
                text = text,
                columnSpan = columnSpan,
                rowSpan = rowSpan,
                alignment = alignment,
                isHeader = isHeaderCell,
                classNames = cell.classNames(),
                width = width
            )
        }
        return RichTextTableRow(cells = cells, isHeader = isHeaderRow, classNames = buildRowClassSet(row, tableElement))
    }

    private fun buildRowClassSet(row: KsoupElement, tableElement: KsoupElement): Set<String> {
        return buildSet {
            row.classNames().forEach { if (it.isNotBlank()) add(it) }
            row.collectAncestorClasses(tableElement).forEach { if (it.isNotBlank()) add(it) }
            tableElement.classNames().forEach { if (it.isNotBlank()) add(it) }
        }
    }

    private fun parseWidth(widthAttr: String, styleAttr: String): Float? {
        if (styleAttr.contains("width")) {
            val styleVal = styleAttr.substringAfter("width").substringAfter(":").substringBefore(";").trim()
            parseDimension(styleVal)?.let { return it }
        }
        if (widthAttr.isNotBlank()) {
            parseDimension(widthAttr)?.let { return it }
        }
        return null
    }

    private fun parseDimension(value: String): Float? {
        val clean = value.trim().lowercase()
        if (clean.endsWith("%")) {
            return clean.removeSuffix("%").toFloatOrNull()?.div(100f)
        }
        if (clean.endsWith("px")) {
            return clean.removeSuffix("px").toFloatOrNull()
        }
        return clean.toFloatOrNull()
    }

    private fun parseAbstractBlock(element: KsoupElement): RichTextBlock.AbstractBlock? {
        val childBlocks = parse(element.children).toMutableList()
        if (childBlocks.isEmpty()) return null
        var title: AnnotatedString? = null
        if (childBlocks.firstOrNull() is RichTextBlock.Heading) {
            val heading = childBlocks.removeAt(0) as RichTextBlock.Heading
            title = heading.text
        }
        return RichTextBlock.AbstractBlock(
            title = title,
            blocks = childBlocks,
            classNames = element.classNames()
        )
    }

    private fun parseMediaElement(element: KsoupElement, inheritedTextAlign: TextAlign? = null): RichTextBlock.Media? {
        val source = element.attr("src").takeIf { it.isNotBlank() } ?: return null
        val description = element.attr("alt").takeIf { it.isNotBlank() }
            ?: element.attr("title").takeIf { it.isNotBlank() }
        val width = element.attr("width").toIntOrNull()
        val height = element.attr("height").toIntOrNull()
        val alignment = when (element.attr("align").lowercase()) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.End
            else -> inheritedTextAlign ?: TextAlign.Start
        }
        val mediaRef = element.attr("data-filename").takeIf { it.isNotBlank() } ?: extractMediaRef(source)
        return RichTextBlock.Media(
            source = source,
            mediaRef = mediaRef,
            description = description,
            width = width,
            height = height,
            alignment = alignment,
            classNames = element.classNames()
        )
    }

    private fun KsoupElement.isTooltipContentNode(): Boolean {
        if (classNames().containsAnyInsensitive(tooltipContentClassNames)) return true
        val role = attr("data-role")
        if (role.equals("tooltip", true)) return true
        val part = attr("data-tooltip-part")
        if (part.equals("content", true)) return true
        if (hasAttr("data-tooltip-content") || hasAttr("data-tooltip-text")) return true
        val tooltipRole = attr("data-tooltip-role")
        if (tooltipRole.equals("content", true)) return true
        val type = attr("data-type")
        if (type.equals("tooltip", true)) return true
        return false
    }

    private fun extractTooltipText(element: KsoupElement): String? {
        tooltipAttributeCandidates.forEach { attrName ->
            val value = element.attr(attrName)
            if (value.isNotBlank()) {
                parseTooltipPayload(value)?.let { return it }
            }
        }
        element.attr("data-tooltip-json").takeIf { it.isNotBlank() }?.let { candidate ->
            parseTooltipPayload(candidate)?.let { return it }
        }

        val inlineNode = findInlineTooltipNode(element)
        val inlineText = inlineNode?.text()?.trim()
        return inlineText?.takeIf { it.isNotEmpty() }
    }

    private fun findInlineTooltipNode(element: KsoupElement): KsoupElement? {
        if (element.isTooltipContentNode()) return element
        for (child in element.children) {
            if (child is KsoupElement) {
                val found = findInlineTooltipNode(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun parseTooltipPayload(rawValue: String): String? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null

        try {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = Json.parseToJsonElement(trimmed).jsonObject
                val keys = listOf("description", "text", "content", "value", "body", "tooltip", "message")
                for (key in keys) {
                    val value = json[key]?.jsonPrimitive?.contentOrNull
                    if (!value.isNullOrBlank()) {
                        return stripHtml(value)
                    }
                }
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val array = Json.parseToJsonElement(trimmed).jsonArray
                for (element in array) {
                    val candidate = if (element is kotlinx.serialization.json.JsonObject) {
                        val keys = listOf("description", "text", "content", "value", "body", "tooltip", "message")
                        keys.firstNotNullOfOrNull { key -> element[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
                    } else {
                        element.jsonPrimitive.contentOrNull
                    }
                    if (!candidate.isNullOrBlank()) {
                        return stripHtml(candidate)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore json errors
        }

        return stripHtml(trimmed).takeIf { it.isNotEmpty() }
    }

    private fun stripHtml(html: String): String {
        val handler = object : KsoupHtmlHandler {
            val sb = StringBuilder()
            override fun onText(text: String) {
                sb.append(text)
            }
        }
        val parser = KsoupHtmlParser(handler)
        parser.write(html)
        parser.end()
        return handler.sb.toString().trim()
    }

    companion object {
        private val tooltipAttributeCandidates = listOf(
            "data-tooltip",
            "data-tooltip-text",
            "data-tooltip-content",
            "data-smartip",
            "data-smarttip",
            "miamed-smartip",
            "data-description",
            "data-desc",
            "data-term-description",
            "data-info",
            "data-message",
            "data-details",
            "data-content",
            "data-title",
            "title"
        )

        private val tooltipContentClassNames = setOf(
            "tooltiptext",
            "tooltip-text",
            "tooltip-content",
            "tooltip__content",
            "annotation-description",
            "annotation__description",
            "smartip-description",
            "smartip__description",
            "smartip-content",
            "smartip__content"
        )
    }
}

private fun InlineStyle.applyClassStyles(
    classes: Set<String>,
    palette: RichTextPalette,
    showSelectedHighlight: Boolean
): InlineStyle {
    if (classes.isEmpty()) return this
    var updated = this
    classes.forEach { rawClass ->
        when (rawClass.lowercase()) {
            "wichtig" -> updated = updated.copy(highlight = InlineHighlight.IMPORTANT, bold = true)
            "selected" -> if (showSelectedHighlight) {
                updated = updated.copy(highlight = InlineHighlight.SELECTED)
            }
            "dictionary" -> updated = updated.copy(dictionary = true, underline = true)
            "nowrap" -> updated = updated.copy(preserveWhitespace = true)
            "scientific-name" -> updated = updated.copy(italic = true)
            "abstract" -> updated = updated.copy(smallText = true, textColor = palette.abstractText)
            "metalink" -> updated = updated.copy(textColor = Color(0xFFE91E63), italic = true)
        }
    }
    return updated
}

private fun AnnotatedString.Builder.appendTextWithStyle(
    text: String,
    style: InlineStyle,
    palette: RichTextPalette
) {
    if (text.isEmpty()) return
    val displayText = if (style.preserveWhitespace) text.replace(' ', '\u00A0') else text
    val textColor = when {
        style.textColor != null -> style.textColor
        style.highlight == InlineHighlight.IMPORTANT -> palette.importantText
        style.highlight == InlineHighlight.SELECTED -> palette.selectedText
        style.dictionary -> palette.dictionaryText
        style.tooltip != null -> palette.dictionaryText
        else -> null
    }
    val backgroundColor = when (style.highlight) {
        InlineHighlight.IMPORTANT -> palette.importantBackground
        InlineHighlight.SELECTED -> palette.selectedBackground
        null -> Color.Unspecified
    }
    val needsUnderline = style.underline || style.dictionary || style.tooltip != null
    val spanStyle = SpanStyle(
        fontWeight = if (style.bold) FontWeight.SemiBold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        textDecoration = if (needsUnderline) TextDecoration.Underline else null,
        fontFamily = if (style.monospace) FontFamily.Monospace else FontFamily.Default,
        baselineShift = when {
            style.superscript -> BaselineShift.Superscript
            style.subscript -> BaselineShift.Subscript
            else -> BaselineShift.None
        },
        background = backgroundColor,
        color = textColor ?: Color.Unspecified,
        fontSize = if (style.smallText) 12.sp else TextUnit.Unspecified
    )
    if (style.link != null) {
        pushStringAnnotation(tag = "URL", annotation = style.link)
    }
    if (style.tooltip != null) {
        pushStringAnnotation(tag = "TOOLTIP", annotation = style.tooltip)
    }
    withStyle(spanStyle) {
        append(displayText)
    }
    if (style.tooltip != null) {
        pop()
    }
    if (style.link != null) {
        pop()
    }
}

private fun mediaModelForSource(source: String, mediaRef: String?): Any? {
    val filename = mediaRef ?: extractMediaRef(source)
    if (filename != null) {
        HtmlUtils.getMediaPath(filename)?.let { path ->
            // Coil 3 supports file paths as strings
            return path
        }
    }
    if (source.startsWith("file://")) {
        return source.removePrefix("file://")
    }
    return source
}

private fun extractMediaRef(source: String): String? {
    return source.substringAfterLast('/', "").takeIf { it.isNotBlank() }
}
