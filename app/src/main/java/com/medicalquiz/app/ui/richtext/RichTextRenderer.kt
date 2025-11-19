package com.medicalquiz.app.ui.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.medicalquiz.app.utils.HtmlUtils
import java.io.File
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

sealed interface RichTextBlock {
    data class Paragraph(val text: AnnotatedString) : RichTextBlock
    data class Heading(val level: Int, val text: AnnotatedString) : RichTextBlock
    data class BulletList(val items: List<AnnotatedString>) : RichTextBlock
    data class OrderedList(val items: List<AnnotatedString>, val start: Int) : RichTextBlock
    data class CodeBlock(val text: String) : RichTextBlock
    data class Table(
        val header: RichTextTableRow?,
        val rows: List<RichTextTableRow>,
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
    val cells: List<AnnotatedString>,
    val isHeader: Boolean
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
    val textColor: Color? = null
)

private enum class InlineHighlight { IMPORTANT, SELECTED }

@Composable
fun RichText(
    html: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null
) {
    val palette = rememberRichTextPalette()
    val blocks = remember(html, palette) { parseRichText(html, palette) }
    RichText(blocks = blocks, modifier = modifier, onLinkClick = onLinkClick, onMediaClick = onMediaClick)
}

@Composable
fun RichText(
    blocks: List<RichTextBlock>,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null
) {
    if (blocks.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    val resolvedLinkHandler: (String) -> Unit = onLinkClick ?: { url ->
        runCatching { uriHandler.openUri(url) }
    }
    val resolvedMediaHandler: (String) -> Unit = onMediaClick ?: {}
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is RichTextBlock.Paragraph -> {
                    RichTextParagraph(block.text, onLinkClick = resolvedLinkHandler)
                }
                is RichTextBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineSmall
                        3 -> MaterialTheme.typography.titleLarge
                        4 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    androidx.compose.material3.Text(
                        text = block.text,
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Visible
                    )
                }
                is RichTextBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.material3.Text(
                                    text = "\u2022",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                RichTextParagraph(
                                    text = item,
                                    modifier = Modifier.weight(1f),
                                    onLinkClick = resolvedLinkHandler
                                )
                            }
                        }
                    }
                }
                is RichTextBlock.OrderedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var counter = block.start
                        block.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.material3.Text(
                                    text = "${'$'}counter.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                RichTextParagraph(
                                    text = item,
                                    modifier = Modifier.weight(1f),
                                    onLinkClick = resolvedLinkHandler
                                )
                            }
                            counter += 1
                        }
                    }
                }
                is RichTextBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .padding(12.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is RichTextBlock.Table -> {
                    RichTextTable(block)
                }
                is RichTextBlock.AbstractBlock -> {
                    AbstractCard(block)
                }
                is RichTextBlock.Media -> {
                    RichMedia(block = block, onMediaClick = resolvedMediaHandler)
                }
                RichTextBlock.Divider -> HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RichTextParagraph(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit
) {
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val hasLinks = remember(text) { text.getStringAnnotations(tag = "URL", start = 0, end = text.length).isNotEmpty() }
    val pointerModifier = if (hasLinks) {
        Modifier.pointerInput(text, layoutResult) {
            detectTapGestures { position ->
                val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation -> onLinkClick(annotation.item) }
            }
        }
    } else {
        Modifier
    }

    androidx.compose.material3.Text(
        text = text,
        modifier = modifier.then(pointerModifier),
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
            textIndent = TextIndent.None
        ),
        onTextLayout = { layoutResult = it }
    )
}

@Composable
private fun RichTextTable(block: RichTextBlock.Table) {
    if (block.columnCount == 0) return
    val scrollState = rememberScrollState()
    val minWidth = 120.dp * block.columnCount
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
                block.header?.let { header ->
                    TableRowContent(row = header, columnCount = block.columnCount, isHeader = true)
                    if (block.rows.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
                block.rows.forEachIndexed { index, row ->
                    TableRowContent(row = row, columnCount = block.columnCount, isHeader = false)
                    if (index != block.rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
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
private fun TableRowContent(row: RichTextTableRow, columnCount: Int, isHeader: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = when {
                    isHeader -> MaterialTheme.colorScheme.secondaryContainer
                    row.isHeader -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(columnCount) { columnIndex ->
            val cellText = row.cells.getOrNull(columnIndex) ?: AnnotatedString("")
            androidx.compose.material3.Text(
                text = cellText,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                style = if (isHeader || row.isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                color = if (isHeader || row.isHeader) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible
            )
        }
    }
}

@Composable
private fun AbstractCard(block: RichTextBlock.AbstractBlock) {
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
                RichText(blocks = block.blocks)
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

private fun parseRichText(html: String, palette: RichTextPalette): List<RichTextBlock> {
    val document = Jsoup.parseBodyFragment(html)
    return parseNodes(document.body().childNodes(), palette)
}

private fun parseNodes(nodes: List<Node>, palette: RichTextPalette): List<RichTextBlock> {
    val blocks = mutableListOf<RichTextBlock>()
    nodes.forEach { node ->
        when (node) {
            is TextNode -> {
                val text = node.text().trim()
                if (text.isNotEmpty()) {
                    blocks += RichTextBlock.Paragraph(buildAnnotatedString { append(text) })
                }
            }
            is Element -> {
                when (val tag = node.tagName().lowercase()) {
                    "p" -> {
                        val mediaElements = node.select("img")
                        val textOnly = node.clone()
                        textOnly.select("img").remove()
                        buildAnnotatedBlock(textOnly, palette)?.let { paragraph ->
                            if (paragraph.text.isNotBlank()) blocks += RichTextBlock.Paragraph(paragraph)
                        }
                        mediaElements.forEach { image ->
                            parseMediaElement(image)?.let(blocks::add)
                        }
                    }
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        val level = tag.removePrefix("h").toIntOrNull() ?: 6
                        buildAnnotatedBlock(node, palette)?.let { heading ->
                            if (heading.text.isNotBlank()) blocks += RichTextBlock.Heading(level, heading)
                        }
                    }
                    "ul" -> {
                        val items = node.children()
                            .mapNotNull { child ->
                                if (child.tagName().equals("li", ignoreCase = true)) buildAnnotatedBlock(child, palette) else null
                            }
                            .filter { it.text.isNotBlank() }
                        if (items.isNotEmpty()) blocks += RichTextBlock.BulletList(items)
                    }
                    "ol" -> {
                        val start = node.attr("start").toIntOrNull() ?: 1
                        val items = node.children()
                            .mapNotNull { child ->
                                if (child.tagName().equals("li", ignoreCase = true)) buildAnnotatedBlock(child, palette) else null
                            }
                            .filter { it.text.isNotBlank() }
                        if (items.isNotEmpty()) blocks += RichTextBlock.OrderedList(items, start)
                    }
                    "hr" -> blocks += RichTextBlock.Divider
                    "pre", "code" -> {
                        val codeText = node.text().trim()
                        if (codeText.isNotEmpty()) blocks += RichTextBlock.CodeBlock(codeText)
                    }
                    "table" -> parseTable(node, palette)?.let(blocks::add)
                    "div", "section", "article" -> {
                        if (node.classNames().any { it.equals("abstract", ignoreCase = true) }) {
                            parseAbstractBlock(node, palette)?.let(blocks::add)
                        } else {
                            blocks += parseNodes(node.childNodes(), palette)
                        }
                    }
                    "img" -> parseMediaElement(node)?.let(blocks::add)
                    else -> {
                        buildAnnotatedBlock(node, palette)?.let { paragraph ->
                            if (paragraph.text.isNotBlank()) blocks += RichTextBlock.Paragraph(paragraph)
                        }
                    }
                }
            }
        }
    }
    return blocks
}

private fun buildAnnotatedBlock(element: Element, palette: RichTextPalette): AnnotatedString? {
    val baseStyle = InlineStyle().applyClassStyles(element.classNames(), palette)
    val builder = buildAnnotatedString {
        appendNodes(element.childNodes(), baseStyle, palette)
    }
    return builder.takeIf { it.text.isNotBlank() }
}

private fun AnnotatedString.Builder.appendNodes(nodes: List<Node>, style: InlineStyle, palette: RichTextPalette) {
    nodes.forEach { node -> appendNode(node, style, palette) }
}

private fun AnnotatedString.Builder.appendNode(node: Node, style: InlineStyle, palette: RichTextPalette) {
    when (node) {
        is TextNode -> {
            val text = node.text().replace('\u00A0', ' ')
            if (text.isNotBlank()) {
                appendTextWithStyle(text, style, palette)
            }
        }
        is Element -> {
            val tag = node.tagName().lowercase()
            if (tag == "br") {
                append("\n")
                return
            }
            var nextStyle = when (tag) {
                "strong", "b" -> style.copy(bold = true)
                "em", "i" -> style.copy(italic = true)
                "u" -> style.copy(underline = true)
                "code" -> style.copy(monospace = true)
                "sup" -> style.copy(superscript = true)
                "sub" -> style.copy(subscript = true)
                "a" -> style.copy(link = node.attr("href"))
                else -> style
            }
            nextStyle = nextStyle.applyClassStyles(node.classNames(), palette)
            appendNodes(node.childNodes(), nextStyle, palette)
        }
    }
}

private fun InlineStyle.applyClassStyles(classes: Set<String>, palette: RichTextPalette): InlineStyle {
    if (classes.isEmpty()) return this
    var updated = this
    classes.forEach { rawClass ->
        when (rawClass.lowercase()) {
            "wichtig" -> updated = updated.copy(highlight = InlineHighlight.IMPORTANT)
            "selected" -> updated = updated.copy(highlight = InlineHighlight.SELECTED)
            "dictionary" -> updated = updated.copy(dictionary = true, underline = true)
            "nowrap" -> updated = updated.copy(preserveWhitespace = true)
            "scientific-name" -> updated = updated.copy(italic = true)
            "abstract" -> updated = updated.copy(smallText = true, textColor = palette.abstractText)
        }
    }
    return updated
}

private fun AnnotatedString.Builder.appendTextWithStyle(text: String, style: InlineStyle, palette: RichTextPalette) {
    if (text.isEmpty()) return
    val displayText = if (style.preserveWhitespace) text.replace(' ', '\u00A0') else text
    val textColor = style.textColor ?: when (style.highlight) {
        InlineHighlight.IMPORTANT -> palette.importantText
        InlineHighlight.SELECTED -> palette.selectedText
        null -> if (style.dictionary) palette.dictionaryText else null
    }
    val backgroundColor = when (style.highlight) {
        InlineHighlight.IMPORTANT -> palette.importantBackground
        InlineHighlight.SELECTED -> palette.selectedBackground
        null -> Color.Unspecified
    }
    val spanStyle = SpanStyle(
        fontWeight = if (style.bold) FontWeight.SemiBold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        textDecoration = if (style.underline || style.dictionary) TextDecoration.Underline else null,
        fontFamily = if (style.monospace) FontFamily.Monospace else FontFamily.Default,
        baselineShift = when {
            style.superscript -> BaselineShift.Superscript
            style.subscript -> BaselineShift.Subscript
            else -> BaselineShift.None
        },
        background = backgroundColor,
        color = textColor ?: Color.Unspecified,
        fontSize = if (style.smallText) 12.sp else Unspecified
    )
    if (style.link != null) {
        pushStringAnnotation(tag = "URL", annotation = style.link)
    }
    withStyle(spanStyle) {
        append(displayText)
    }
    if (style.link != null) {
        pop()
    }
}

private fun parseTable(element: Element, palette: RichTextPalette): RichTextBlock.Table? {
    val headerRow = element.selectFirst("thead tr")?.let { parseTableRow(it, true, palette) }
    val bodyRows = element.select("tbody tr").map { parseTableRow(it, false, palette) }.toMutableList()
    val fallbackRows = if (bodyRows.isEmpty()) {
        element.select("tr").map { parseTableRow(it, false, palette) }.toMutableList()
    } else {
        bodyRows
    }
    var resolvedHeader = headerRow
    if (resolvedHeader == null && fallbackRows.isNotEmpty()) {
        val first = fallbackRows.removeAt(0)
        resolvedHeader = first.copy(isHeader = true)
    }
    val columnCount = listOfNotNull(
        resolvedHeader?.cells?.size,
        fallbackRows.maxOfOrNull { it.cells.size }
    ).maxOrNull() ?: 0
    if (columnCount == 0) return null
    return RichTextBlock.Table(
        header = resolvedHeader,
        rows = fallbackRows,
        columnCount = columnCount,
        classNames = element.classNames()
    )
}

private fun parseTableRow(row: Element, headerContext: Boolean, palette: RichTextPalette): RichTextTableRow {
    val cellElements = row.children().filter { child ->
        val tag = child.tagName().lowercase()
        tag == "td" || tag == "th"
    }
    val isHeader = headerContext || cellElements.all { it.tagName().equals("th", ignoreCase = true) }
    if (cellElements.isEmpty()) {
        return RichTextTableRow(emptyList(), isHeader)
    }
    val cells = cellElements.map { cell ->
        buildAnnotatedBlock(cell, palette) ?: AnnotatedString("")
    }
    return RichTextTableRow(cells = cells, isHeader = isHeader)
}

private fun parseAbstractBlock(element: Element, palette: RichTextPalette): RichTextBlock.AbstractBlock? {
    val childBlocks = parseNodes(element.childNodes(), palette).toMutableList()
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

private fun parseMediaElement(element: Element): RichTextBlock.Media? {
    val source = element.attr("src").takeIf { it.isNotBlank() } ?: return null
    val description = element.attr("alt").takeIf { it.isNotBlank() }
        ?: element.attr("title").takeIf { it.isNotBlank() }
    val width = element.attr("width").toIntOrNull()
    val height = element.attr("height").toIntOrNull()
    val alignment = when (element.attr("align").lowercase()) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        else -> TextAlign.Start
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

private fun extractMediaRef(source: String): String? {
    return source.substringAfterLast('/', "").takeIf { it.isNotBlank() }
}

private fun mediaModelForSource(source: String, mediaRef: String?): Any? {
    val filename = mediaRef ?: extractMediaRef(source)
    if (filename != null) {
        HtmlUtils.getMediaPath(filename)?.let { path ->
            val file = File(path)
            if (file.exists()) {
                return file
            }
        }
    }
    if (source.startsWith("file://")) {
        val sanitized = source.removePrefix("file://")
        val file = File(sanitized)
        if (file.exists()) return file
    }
    return source
}
