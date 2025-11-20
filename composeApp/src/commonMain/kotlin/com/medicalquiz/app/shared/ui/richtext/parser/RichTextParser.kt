package com.medicalquiz.app.shared.ui.richtext.parser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicalquiz.app.shared.ui.richtext.RichTextBlock
import com.medicalquiz.app.shared.ui.richtext.RichTextPalette
import com.medicalquiz.app.shared.ui.richtext.RichTextTableCell
import com.medicalquiz.app.shared.ui.richtext.RichTextTableRow
import com.medicalquiz.app.shared.ui.richtext.extractMediaRef
import com.medicalquiz.app.shared.ui.richtext.matchesAnyMarker
import com.medicalquiz.app.shared.ui.richtext.normalizedMarkers
import com.medicalquiz.app.shared.ui.richtext.containsAnyInsensitive
import com.medicalquiz.app.shared.ui.richtext.containsInsensitive
import com.medicalquiz.app.shared.ui.richtext.normalizeMarker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser

/**
 * Decodes HTML entities (both named and numeric) to their character equivalents.
 * 
 * Handles common entities like &amp;, &lt;, &gt;, &quot;, &#39;, and numeric entities like &#39; and &#x27;.
 * 
 * @param text The text containing HTML entities
 * @return Text with entities decoded to characters
 */
private fun decodeHtmlEntities(text: String): String {
    if (!text.contains('&')) return text
    
    var result = text
    
    // Common named entities
    val namedEntities = mapOf(
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&#39;" to "'",
        "&#x27;" to "'",
        "&nbsp;" to "\u00A0",
        "&ndash;" to "–",
        "&mdash;" to "—",
        "&lsquo;" to "'",
        "&rsquo;" to "'",
        "&ldquo;" to "\"",
        "&rdquo;" to "\"",
        "&hellip;" to "…",
        "&copy;" to "©",
        "&reg;" to "®",
        "&trade;" to "™",
        "&euro;" to "€",
        "&pound;" to "£",
        "&yen;" to "¥",
        "&cent;" to "¢",
        "&deg;" to "°",
        "&plusmn;" to "±",
        "&times;" to "×",
        "&divide;" to "÷",
        "&frac12;" to "½",
        "&frac14;" to "¼",
        "&frac34;" to "¾"
    )
    
    // Replace named entities
    namedEntities.forEach { (entity, char) ->
        result = result.replace(entity, char)
    }
    
    // Decode numeric entities (decimal): &#123;
    val decimalPattern = "&#(\\d+);"
    result = Regex(decimalPattern).replace(result) { matchResult ->
        val codePoint = matchResult.groupValues[1].toIntOrNull()
        if (codePoint != null && codePoint in 1..0x10FFFF) {
            try {
                codePoint.toChar().toString()
            } catch (e: Exception) {
                matchResult.value // Keep original if conversion fails
            }
        } else {
            matchResult.value
        }
    }
    
    // Decode numeric entities (hexadecimal): &#x1F; or &#X1F;
    val hexPattern = "&#[xX]([0-9a-fA-F]+);"
    result = Regex(hexPattern).replace(result) { matchResult ->
        val codePoint = matchResult.groupValues[1].toIntOrNull(16)
        if (codePoint != null && codePoint in 1..0x10FFFF) {
            try {
                codePoint.toChar().toString()
            } catch (e: Exception) {
                matchResult.value
            }
        } else {
            matchResult.value
        }
    }
    
    return result
}

/**
 * Parser for converting HTML to RichTextBlock elements.
 * 
 * This parser handles nested HTML structures, inline styles, tables with rowspan/colspan,
 * tooltips, media elements, and various semantic class markers.
 */
internal object RichTextParser {
    
    // Parsing limits to prevent resource exhaustion
    private const val MAX_RECURSION_DEPTH = 100
    private const val MAX_TABLE_ROWS = 1000
    private const val MAX_TABLE_COLUMNS = 50
    
    // Magic numbers for heuristics
    private const val MAX_TITLE_LENGTH = 200
    private const val BOLD_FONT_WEIGHT_THRESHOLD = 600
    private const val BOLD_CHECK_MAX_DEPTH = 4
    private const val EM_TO_DP_MULTIPLIER = 16f

    /**
     * Parses HTML string into a list of RichTextBlock elements.
     * 
     * @param html The HTML content to parse
     * @param palette Color palette for styling
     * @param showSelectedHighlight Whether to apply visual highlighting to selected elements
     * @return List of parsed RichTextBlock elements
     */
    fun parse(
        html: String,
        palette: RichTextPalette,
        showSelectedHighlight: Boolean
    ): List<RichTextBlock> {
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

private class KsoupTextNode(
    val text: String,
    override val parent: KsoupElement?
) : KsoupNode

private class KsoupElement(
    val tagName: String,
    val attributes: Map<String, String>,
    override val parent: KsoupElement?
) : KsoupNode {
    val children = mutableListOf<KsoupNode>()

    fun attr(name: String): String = attributes[name] ?: ""
    fun hasAttr(name: String): Boolean = attributes.containsKey(name)
    fun classNames(): Set<String> = attributes["class"]?.split(" ")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

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
        cursor.classNames().forEach { if (it.isNotBlank()) classes += it }
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
        // Decode HTML entities in attribute values
        val decodedAttributes = attributes.mapValues { (_, value) -> decodeHtmlEntities(value) }
        val newElement = KsoupElement(name, decodedAttributes, currentElement)
        if (currentElement == null) {
            rootElements.add(newElement)
        } else {
            currentElement?.children?.add(newElement)
        }
        currentElement = newElement
    }

    override fun onText(text: String) {
        if (text.isEmpty()) return
        // Decode HTML entities like &#39; to ' and &amp; to &
        val decodedText = decodeHtmlEntities(text)
        val textNode = KsoupTextNode(decodedText, currentElement)
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
        inheritedStyles: InheritedStyles = InheritedStyles(),
        depth: Int = 0
    ): List<RichTextBlock> {
        // Prevent stack overflow from deeply nested or malicious HTML
        if (depth >= RichTextParser.MAX_RECURSION_DEPTH) {
            println("RichText: Maximum recursion depth reached at $depth levels")
            return emptyList()
        }
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
                        "p" -> handleParagraph(node, blocks, nextStyles, depth)
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
                                parseAbstractBlock(node, depth + 1)?.let(blocks::add)
                            } else {
                                blocks += parse(node.children, nextStyles, depth + 1)
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
        inheritedStyles: InheritedStyles,
        depth: Int
    ) {
        val inlineNodes = mutableListOf<KsoupNode>()
        val mediaElements = mutableListOf<KsoupElement>()
        val paragraphAlignment = parseTextAlign(node) ?: inheritedStyles.textAlign
        val paragraphBaseStyle = InlineStyle().applyClassStyles(node.classNames(), palette, showSelectedHighlight)
        val nestedInheritedStyles = inheritedStyles.copy(textAlign = paragraphAlignment)

        fun flushInlineParagraph() {
            if (inlineNodes.isEmpty()) return
            val builder = buildAnnotatedString {
                appendNodes(inlineNodes, paragraphBaseStyle, palette)
            }
            inlineNodes.clear()
            if (builder.text.isNotBlank()) {
                blocks += RichTextBlock.Paragraph(
                    text = builder,
                    textAlign = paragraphAlignment ?: TextAlign.Start
                )
            }
        }

        node.children.forEach { child ->
            when {
                child is KsoupElement && child.tagName.equals("img", ignoreCase = true) -> mediaElements.add(child)
                child is KsoupElement && child.isBlockLikeChild() -> {
                    flushInlineParagraph()
                    blocks.addAll(parse(listOf(child), nestedInheritedStyles, depth + 1))
                }
                else -> inlineNodes.add(child)
            }
        }

        flushInlineParagraph()

        mediaElements.forEach { image ->
            parseMediaElement(image, paragraphAlignment)?.let(blocks::add)
        }
    }

    private fun KsoupElement.isBlockLikeChild(): Boolean {
        val tag = tagName.lowercase()
        return blockLevelChildTags.contains(tag)
    }

    private fun buildAnnotatedBlock(element: KsoupElement): AnnotatedString? {
        val baseStyle = InlineStyle().applyClassStyles(element.classNames(), palette, showSelectedHighlight)
        val builder = buildAnnotatedString {
            appendNodes(element.children, baseStyle, palette)
        }
        return builder.takeIf { it.text.isNotBlank() }
    }

    private fun AnnotatedString.Builder.appendNodes(
        nodes: List<KsoupNode>,
        style: InlineStyle,
        palette: RichTextPalette
    ) {
        nodes.forEach { node -> appendNode(node, style, palette) }
    }

    private fun AnnotatedString.Builder.appendNode(
        node: KsoupNode,
        style: InlineStyle,
        palette: RichTextPalette
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
                appendNodes(node.children, nextStyle, palette)

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
        
        // Prevent memory exhaustion from excessively large tables
        if (allRows.size > RichTextParser.MAX_TABLE_ROWS) {
            println("RichText: Table has ${allRows.size} rows, limiting to ${RichTextParser.MAX_TABLE_ROWS}")
            while (allRows.size > RichTextParser.MAX_TABLE_ROWS) {
                allRows.removeLast()
            }
        }

        val headerRows = mutableListOf<RichTextTableRow>()
        val bodyRows = mutableListOf<RichTextTableRow>()

        allRows.forEachIndexed { index, tr ->
            var isHeaderContext = false
            var parent = tr.parent
            while (parent != null && parent != element) {
                if (parent.tagName.equals("thead", ignoreCase = true)) {
                    isHeaderContext = true
                    break
                }
                parent = parent.parent
            }

            val parsedRow = parseTableRow(tr, element, isHeaderContext, index == 0)
            if (parsedRow.isHeader) {
                headerRows.add(parsedRow)
            } else {
                bodyRows.add(parsedRow)
            }
        }

        var columnCount = (headerRows + bodyRows)
            .maxOfOrNull { row ->
                row.cells.sumOf { cell -> cell.columnSpan.coerceAtLeast(1) }
            } ?: 0
        if (columnCount == 0) return null
        
        // Prevent memory exhaustion from tables with too many columns
        if (columnCount > RichTextParser.MAX_TABLE_COLUMNS) {
            println("RichText: Table has $columnCount columns, limiting to ${RichTextParser.MAX_TABLE_COLUMNS}")
            columnCount = RichTextParser.MAX_TABLE_COLUMNS
        }

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
        headerContext: Boolean,
        isFirstRow: Boolean
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

        val rowClasses = buildRowClassSet(row, tableElement)
        if (cellElements.isEmpty()) {
            val hasHeaderMarkers = headerContext ||
                row.classNames().matchesAnyMarker(headerRowClassMarkers) ||
                row.hasHeaderAttributeMarker(headerRowAttributeNames)
            return RichTextTableRow(emptyList(), hasHeaderMarkers, rowClasses)
        }

        val cellInfos = cellElements.map { cell ->
            val classes = cell.classNames()
            val text = buildAnnotatedBlock(cell) ?: AnnotatedString("")
            val columnSpan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val rowSpan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val alignment = resolveCellAlignment(cell, classes)
            val width = parseWidth(cell.attr("width"), cell.attr("style"))
            val paddingStart = parsePaddingStart(cell.attr("style"))
            val hasHeaderTraits = cell.isHeaderCellCandidate(classes)
            CellInfo(
                text = text,
                rawText = cell.text().trim(),
                columnSpan = columnSpan,
                rowSpan = rowSpan,
                alignment = alignment,
                width = width,
                paddingStart = paddingStart,
                classNames = classes,
                hasHeaderTraits = hasHeaderTraits
            )
        }

        val isHeaderRow = isTableRowHeader(row, cellInfos, headerContext, isFirstRow)

        val cells = cellInfos.map { info ->
            RichTextTableCell(
                text = info.text,
                columnSpan = info.columnSpan,
                rowSpan = info.rowSpan,
                alignment = info.alignment,
                isHeader = isHeaderRow || info.hasHeaderTraits,
                classNames = info.classNames,
                width = info.width,
                paddingStart = info.paddingStart
            )
        }
        return RichTextTableRow(cells = cells, isHeader = isHeaderRow, classNames = rowClasses)
    }

    /**
     * Determines if a table row should be treated as a header row.
     * Uses multiple heuristics including explicit markers, cell traits, and layout patterns.
     */
    private fun isTableRowHeader(
        row: KsoupElement,
        cellInfos: List<*>,
        headerContext: Boolean,
        isFirstRow: Boolean
    ): Boolean {
        // Type-safe access to CellInfo - we know this is the actual type from parseTableRow
        @Suppress("UNCHECKED_CAST")
        fun getCellInfo(index: Int): CellInfo? = cellInfos.getOrNull(index) as? CellInfo
        
        // Explicit header markers
        if (headerContext) return true
        if (row.classNames().matchesAnyMarker(headerRowClassMarkers)) return true
        if (row.hasHeaderAttributeMarker(headerRowAttributeNames)) return true
        if (row.classNames().matchesAnyMarker(titleRowClassMarkers)) return true
        
        // All cells are marked as headers
        val allCellsHeader = cellInfos.all { (it as? CellInfo)?.hasHeaderTraits == true }
        if (allCellsHeader && cellInfos.isNotEmpty()) return true
        
        // Single-cell title row heuristic
        if (cellInfos.size == 1) {
            val info = getCellInfo(0) ?: return false
            val textLength = info.rawText.length
            if (textLength > 0 && textLength <= RichTextParser.MAX_TITLE_LENGTH) {
                val rowAlignment = parseTextAlign(row)
                val centerAligned = info.alignment == TextAlign.Center || rowAlignment == TextAlign.Center
                val spansMultiple = info.columnSpan >= 2
                val rowHasTitleClass = row.classNames().matchesAnyMarker(titleRowClassMarkers)
                val rowHasHeaderAttrs = row.hasHeaderAttributeMarker(headerRowAttributeNames)
                val emphasised = info.hasHeaderTraits || rowHasTitleClass || rowHasHeaderAttrs
                
                return (centerAligned || emphasised) && (spansMultiple || isFirstRow)
            }
        }
        
        return false
    }
    
    // CellInfo data class moved from local scope to be accessible to helper method
    private data class CellInfo(
        val text: AnnotatedString,
        val rawText: String,
        val columnSpan: Int,
        val rowSpan: Int,
        val alignment: TextAlign,
        val width: Float?,
        val paddingStart: Dp,
        val classNames: Set<String>,
        val hasHeaderTraits: Boolean
    )

    private fun buildRowClassSet(row: KsoupElement, tableElement: KsoupElement): Set<String> {
        return buildSet {
            row.classNames().forEach { if (it.isNotBlank()) add(it) }
            row.collectAncestorClasses(tableElement).forEach { if (it.isNotBlank()) add(it) }
            tableElement.classNames().forEach { if (it.isNotBlank()) add(it) }
        }
    }

    private fun parseWidth(widthAttr: String, styleAttr: String): Float? {
        extractCssValue(styleAttr, "width")?.let { value ->
            parseDimension(value)?.let { return it }
        }
        extractCssValue(styleAttr, "min-width")?.let { value ->
            parseDimension(value)?.let { return it }
        }
        extractCssValue(styleAttr, "max-width")?.let { value ->
            parseDimension(value)?.let { return it }
        }
        if (widthAttr.isNotBlank()) {
            parseDimension(widthAttr)?.let { return it }
        }
        return null
    }

    private fun extractCssValue(styleAttr: String, property: String): String? {
        if (styleAttr.isBlank()) return null
        styleAttr.split(";").forEach { declaration ->
            val name = declaration.substringBefore(":").trim()
            if (name.equals(property, ignoreCase = true)) {
                val rawValue = declaration.substringAfter(":", "")
                    .substringBefore("!important")
                    .trim()
                if (rawValue.isNotEmpty()) return rawValue
            }
        }
        return null
    }

    private fun parseDimension(value: String): Float? {
        val clean = value.trim().lowercase()
        if (clean.endsWith("%")) {
            return clean.removeSuffix("%")
                .toFloatOrNull()
                ?.div(100f)
        }
        if (clean.endsWith("px")) {
            return clean.removeSuffix("px").toFloatOrNull()
        }
        return clean.toFloatOrNull()
    }

    private fun parsePaddingStart(styleAttr: String): Dp {
        val padding = extractCssValue(styleAttr, "padding-left") ?: return 0.dp
        if (padding.endsWith("em")) {
            val value = padding.removeSuffix("em").toFloatOrNull() ?: 0f
            return (value * RichTextParser.EM_TO_DP_MULTIPLIER).dp
        }
        if (padding.endsWith("px")) {
            val value = padding.removeSuffix("px").toFloatOrNull() ?: 0f
            return value.dp
        }
        return 0.dp
    }

    private fun resolveCellAlignment(cell: KsoupElement, classes: Set<String>): TextAlign {
        parseTextAlign(cell)?.let { return it }
        if (classes.matchesAnyMarker(centerAlignmentClassMarkers)) return TextAlign.Center
        if (classes.matchesAnyMarker(endAlignmentClassMarkers)) return TextAlign.End
        return TextAlign.Start
    }

    private fun KsoupElement.isHeaderCellCandidate(classNames: Set<String>): Boolean {
        if (tagName.equals("th", ignoreCase = true)) return true
        if (classNames.matchesAnyMarker(headerCellClassMarkers)) return true
        if (hasHeaderAttributeMarker(headerCellAttributeNames)) return true
        val scope = attr("scope")
        if (scope.equals("col", true) || scope.equals("colgroup", true) || scope.equals("row", true) || scope.equals("rowgroup", true)) return true
        val role = attr("role")
        if (role.equals("columnheader", true) || role.equals("rowheader", true)) return true
        if (containsBoldContent()) return true
        return false
    }

    private fun KsoupElement.hasHeaderAttributeMarker(attributeNames: List<String>): Boolean {
        attributeNames.forEach { attrName ->
            val value = attr(attrName)
            if (value.isBlank()) return@forEach
            if (attrName.equals("scope", true)) {
                if (value.equals("col", true) || value.equals("colgroup", true) || value.equals("row", true) || value.equals("rowgroup", true)) {
                    return true
                }
            }
            val normalizedValue = value.trim()
            val attrImpliesHeader = attrName.contains("header", true) ||
                attrName.contains("title", true) ||
                attrName.contains("caption", true) ||
                attrName.contains("heading", true)
            if (attrImpliesHeader) {
                if (normalizedValue.equals("false", true) || normalizedValue.equals("0")) return@forEach
                if (attrName.contains("title", true) || attrName.contains("caption", true) || attrName.contains("heading", true)) {
                    if (normalizedValue.isNotEmpty()) return true
                }
                if (normalizedValue.equals("true", true) || normalizedValue.equals("1") || normalizedValue.equals("yes", true)) return true
            }
            if (headerAttributeValues.any { candidate -> normalizedValue.contains(candidate, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    private fun KsoupElement.containsBoldContent(maxDepth: Int = RichTextParser.BOLD_CHECK_MAX_DEPTH): Boolean {
        if (maxDepth <= 0) return false
        if (tagName.equals("strong", true) || tagName.equals("b", true)) return true
        if (styleIndicatesBold(attr("style"))) return true
        if (classNames().matchesAnyMarker(boldClassMarkers)) return true
        children.forEach { child ->
            if (child is KsoupElement && child.containsBoldContent(maxDepth - 1)) {
                return true
            }
        }
        return false
    }

    private fun styleIndicatesBold(styleAttr: String): Boolean {
        if (styleAttr.isBlank()) return false
        val fontWeight = extractCssValue(styleAttr, "font-weight")?.lowercase()?.trim() ?: return false
        if (fontWeight.startsWith("bold") || fontWeight.startsWith("bolder")) return true
        val numeric = fontWeight.filter { it.isDigit() }
        return numeric.toIntOrNull()?.let { it >= RichTextParser.BOLD_FONT_WEIGHT_THRESHOLD } == true
    }

    private fun parseAbstractBlock(element: KsoupElement, depth: Int): RichTextBlock.AbstractBlock? {
        val childBlocks = parse(element.children, depth = depth + 1).toMutableList()
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

        private val headerRowClassMarkers = normalizedMarkers(
            "header",
            "table-header",
            "table_header",
            "tableheader",
            "table-header-row",
            "tableheaderrow",
            "thead",
            "tablehead",
            "column-header-row",
            "columnheaderrow",
            "ueberschrift",
            "titelzeile",
            "section-header",
            "subheader",
            "table-heading",
            "tableheading"
        )

        private val titleRowClassMarkers = normalizedMarkers(
            "table-title",
            "tabletitle",
            "table-caption",
            "tablecaption",
            "caption-row",
            "captionrow",
            "legend-row",
            "legendrow",
            "data-table-title",
            "datatabletitle"
        )

        private val headerCellClassMarkers = normalizedMarkers(
            "header",
            "table-header",
            "tableheader",
            "column-header",
            "columnheader",
            "row-header",
            "rowheader",
            "table-head",
            "tablehead",
            "col-header",
            "colheader",
            "ueberschrift",
            "title-cell",
            "titlecell",
            "label-cell",
            "labelcell"
        )

        private val boldClassMarkers = normalizedMarkers(
            "bold",
            "text-bold",
            "fw-bold",
            "fwbold",
            "font-weight-bold",
            "fontweightbold",
            "strong",
            "important"
        )

        private val centerAlignmentClassMarkers = normalizedMarkers(
            "text-center",
            "text-centre",
            "align-center",
            "centered",
            "centre-text",
            "ta-center",
            "tacentre",
            "center-text"
        )

        private val endAlignmentClassMarkers = normalizedMarkers(
            "text-right",
            "text-end",
            "align-right",
            "align-end",
            "ta-right",
            "taright",
            "text-right-align",
            "textright"
        )

        private val headerAttributeValues = setOf(
            "header",
            "heading",
            "title",
            "label",
            "legend",
            "summary",
            "caption",
            "topic",
            "thead"
        )

        private val headerRowAttributeNames = listOf(
            "data-row-type",
            "data-type",
            "role",
            "data-role",
            "aria-role",
            "data-section",
            "data-header",
            "data-caption",
            "data-title",
            "data-heading"
        )

        private val headerCellAttributeNames = listOf(
            "data-cell-type",
            "data-type",
            "role",
            "data-role",
            "data-header",
            "data-heading",
            "headers",
            "scope"
        )

        private val blockLevelChildTags = setOf(
            "div",
            "section",
            "article",
            "table",
            "ul",
            "ol",
            "dl",
            "figure",
            "figcaption",
            "blockquote",
            "pre",
            "form",
            "header",
            "footer",
            "nav",
            "aside",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "hr"
        )
    }
}

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

private fun InlineStyle.applyClassStyles(
    classes: Set<String>,
    palette: RichTextPalette,
    showSelectedHighlight: Boolean
): InlineStyle {
    if (classes.isEmpty()) return this
    var updated = this
    classes.forEach { rawClass ->
        when (rawClass.lowercase()) {
            "important", "wichtig" -> updated = updated.copy(highlight = InlineHighlight.IMPORTANT, bold = true)
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

