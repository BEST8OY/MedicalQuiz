package com.medicalquiz.app.shared.ui.richtext.parser

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.medicalquiz.app.shared.ui.richtext.RichTextBlock
import com.medicalquiz.app.shared.ui.richtext.RichTextPalette
import com.medicalquiz.app.shared.ui.richtext.RichTextTableCell
import com.medicalquiz.app.shared.ui.richtext.RichTextTableRow
import com.medicalquiz.app.shared.ui.richtext.containsInsensitive
import com.medicalquiz.app.shared.ui.richtext.extractMediaRef
import com.medicalquiz.app.shared.ui.richtext.matchesAnyMarker
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser

/**
 * Parser for converting HTML to RichTextBlock elements.
 *
 * This parser handles nested HTML structures, inline styles, tables with rowspan/colspan,
 * tooltips, media elements, and various semantic class markers.
 */
internal object RichTextParser {

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

/**
 * HTML handler that builds a DOM tree from parsed HTML.
 */
private class RichTextHandler(
    private val palette: RichTextPalette,
    private val showSelectedHighlight: Boolean
) : KsoupHtmlHandler {

    val blocks = mutableListOf<RichTextBlock>()
    private var currentElement: KsoupElement? = null
    private val rootElements = mutableListOf<KsoupNode>()

    override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
        // Decode HTML entities in attribute values
        val decodedAttributes = attributes.mapValues { (_, value) -> HtmlEntities.decode(value) }
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
        val decodedText = HtmlEntities.decode(text)
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

/**
 * DOM parser that converts KsoupNode trees into RichTextBlock elements.
 */
private class RichTextDomParser(
    private val palette: RichTextPalette,
    private val showSelectedHighlight: Boolean
) {

    data class InheritedStyles(val textAlign: TextAlign? = null)

    fun parse(
        nodes: List<KsoupNode>,
        inheritedStyles: InheritedStyles = InheritedStyles(),
        depth: Int = 0
    ): List<RichTextBlock> {
        // Prevent stack overflow from deeply nested or malicious HTML
        if (depth >= RichTextParserConfig.MAX_RECURSION_DEPTH) {
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
                    if (RichTextParserConfig.ignoredTagNames.contains(tag)) return@forEach

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
                                    if (child is KsoupElement && child.tagName.equals("li", ignoreCase = true)) {
                                        buildAnnotatedBlock(child)
                                    } else null
                                }
                                .filter { it.text.isNotBlank() }
                            if (items.isNotEmpty()) blocks += RichTextBlock.BulletList(items)
                        }
                        "ol" -> {
                            val start = node.attr("start").toIntOrNull() ?: 1
                            val items = node.children
                                .mapNotNull { child ->
                                    if (child is KsoupElement && child.tagName.equals("li", ignoreCase = true)) {
                                        buildAnnotatedBlock(child)
                                    } else null
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
                        "div", "section", "article", "blockquote" -> {
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
        return CssParser.parseTextAlign(element.attr("align"), element.attr("style"))
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
                child is KsoupElement && child.tagName.equals("img", ignoreCase = true) -> {
                    mediaElements.add(child)
                }
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
        return RichTextParserConfig.blockLevelChildTags.contains(tag)
    }

    private fun buildAnnotatedBlock(element: KsoupElement): AnnotatedString? {
        val baseStyle = InlineStyle().applyClassStyles(element.classNames(), palette, showSelectedHighlight)
        val builder = buildAnnotatedString {
            appendNodes(element.children, baseStyle, palette)
        }
        // Trim leading/trailing whitespace while preserving annotations
        val trimmed = builder.trim()
        return trimmed.takeIf { it.text.isNotBlank() }
    }

    /**
     * Trims leading and trailing whitespace from an AnnotatedString while preserving annotations.
     */
    private fun AnnotatedString.trim(): AnnotatedString {
        val text = this.text
        val start = text.indexOfFirst { !it.isWhitespace() }
        if (start == -1) return AnnotatedString("")
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        if (start == 0 && end == text.length) return this
        return this.subSequence(start, end)
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
                if (TooltipParser.isTooltipContentNode(node)) return
                val tag = node.tagName.lowercase()
                if (RichTextParserConfig.ignoredTagNames.contains(tag)) return

                if (tag == "br") {
                    append("\n")
                    return
                }

                if (tag == "li") {
                    val parent = node.parent
                    val currentLength = this.toAnnotatedString().length
                    val endsWithNewline = currentLength > 0 && this.toAnnotatedString()[currentLength - 1] == '\n'
                    val prefix = if (currentLength == 0 || endsWithNewline) "" else "\n"
                    if (parent != null && parent.tagName.equals("ol", ignoreCase = true)) {
                        val index = parent.children
                            .filter { it is KsoupElement && it.tagName.equals("li", ignoreCase = true) }
                            .indexOf(node)
                        append("$prefix${index + 1}. ")
                    } else {
                        append("$prefix• ")
                    }
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
                        if (href != null) {
                            val normalizedHref = href
                                .substringBefore('#')
                                .substringBefore('?')
                                .trim()
                            val isHtmlLink = normalizedHref.endsWith(".html", ignoreCase = true) ||
                                normalizedHref.endsWith(".htm", ignoreCase = true)
                            if (isHtmlLink) style.copy(link = href, italic = true) else style.copy(link = href)
                        } else {
                            style
                        }
                    }
                    else -> style
                }

                nextStyle = nextStyle.applyClassStyles(node.classNames(), palette, showSelectedHighlight)
                TooltipParser.extractTooltipText(node)?.let { tooltip ->
                    nextStyle = nextStyle.copy(tooltip = tooltip)
                }

                appendNodes(node.children, nextStyle, palette)

                if (tag == "p" || tag == "div") {
                    append("\n")
                }
            }
        }
    }

    // ==================== TABLE PARSING ====================

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
        if (allRows.size > RichTextParserConfig.MAX_TABLE_ROWS) {
            println("RichText: Table has ${allRows.size} rows, limiting to ${RichTextParserConfig.MAX_TABLE_ROWS}")
            while (allRows.size > RichTextParserConfig.MAX_TABLE_ROWS) {
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
        if (columnCount > RichTextParserConfig.MAX_TABLE_COLUMNS) {
            println("RichText: Table has $columnCount columns, limiting to ${RichTextParserConfig.MAX_TABLE_COLUMNS}")
            columnCount = RichTextParserConfig.MAX_TABLE_COLUMNS
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
                row.classNames().matchesAnyMarker(RichTextParserConfig.headerRowClassMarkers) ||
                row.hasHeaderAttributeMarker()
            return RichTextTableRow(emptyList(), hasHeaderMarkers, rowClasses)
        }

        val cellInfos = cellElements.map { cell ->
            val classes = cell.classNames()
            val text = buildAnnotatedBlock(cell) ?: AnnotatedString("")
            val columnSpan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val rowSpan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val alignment = resolveCellAlignment(cell, classes)
            val width = CssParser.parseWidth(cell.attr("width"), cell.attr("style"))
            val paddingStart = CssParser.parsePaddingStart(cell.attr("style"))
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

    private fun isTableRowHeader(
        row: KsoupElement,
        cellInfos: List<CellInfo>,
        headerContext: Boolean,
        isFirstRow: Boolean
    ): Boolean {
        // Explicit header markers
        if (headerContext) return true
        if (row.classNames().matchesAnyMarker(RichTextParserConfig.headerRowClassMarkers)) return true
        if (row.hasHeaderAttributeMarker()) return true
        if (row.classNames().matchesAnyMarker(RichTextParserConfig.titleRowClassMarkers)) return true

        // All cells are marked as headers
        val allCellsHeader = cellInfos.all { it.hasHeaderTraits }
        if (allCellsHeader && cellInfos.isNotEmpty()) return true

        // Single-cell title row heuristic
        if (cellInfos.size == 1) {
            val info = cellInfos.first()
            val rawText = info.rawText.trim()
            val textLength = rawText.length
            val alphanumericCount = rawText.count { it.isLetterOrDigit() }
            val isMeaningfulTitle = textLength in 1..RichTextParserConfig.MAX_TITLE_LENGTH &&
                alphanumericCount > 0 &&
                alphanumericCount * 2 >= textLength

            if (isMeaningfulTitle) {
                val rowAlignment = parseTextAlign(row)
                val centerAligned = info.alignment == TextAlign.Center || rowAlignment == TextAlign.Center
                val spansMultiple = info.columnSpan >= 2
                val rowHasTitleClass = row.classNames().matchesAnyMarker(RichTextParserConfig.titleRowClassMarkers)
                val rowHasHeaderAttrs = row.hasHeaderAttributeMarker()
                val emphasised = info.hasHeaderTraits || rowHasTitleClass || rowHasHeaderAttrs

                return (centerAligned || emphasised) && isFirstRow
            }
        }

        return false
    }

    private data class CellInfo(
        val text: AnnotatedString,
        val rawText: String,
        val columnSpan: Int,
        val rowSpan: Int,
        val alignment: TextAlign,
        val width: Float?,
        val paddingStart: androidx.compose.ui.unit.Dp,
        val classNames: Set<String>,
        val hasHeaderTraits: Boolean
    )

    private fun buildRowClassSet(row: KsoupElement, tableElement: KsoupElement): Set<String> {
        val collected = LinkedHashSet<String>()
        fun addClasses(source: Set<String>) {
            source.forEach { if (it.isNotBlank()) collected += it }
        }
        addClasses(row.classNames())
        addClasses(row.ancestorClasses(tableElement))
        addClasses(tableElement.classNames())
        return collected
    }

    private fun KsoupElement.findAlignmentFromDescendants(
        remainingDepth: Int = RichTextParserConfig.ALIGNMENT_DESCENT_MAX_DEPTH
    ): TextAlign? {
        if (remainingDepth <= 0) return null
        children.forEach { child ->
            if (child is KsoupElement) {
                parseTextAlign(child)?.let { return it }
                val childClasses = child.classNames()
                if (childClasses.matchesAnyMarker(RichTextParserConfig.centerAlignmentClassMarkers)) {
                    return TextAlign.Center
                }
                if (childClasses.matchesAnyMarker(RichTextParserConfig.endAlignmentClassMarkers)) {
                    return TextAlign.End
                }
                child.findAlignmentFromDescendants(remainingDepth - 1)?.let { return it }
            }
        }
        return null
    }

    private fun resolveCellAlignment(cell: KsoupElement, classes: Set<String>): TextAlign {
        parseTextAlign(cell)?.let { return it }
        cell.findAlignmentFromDescendants()?.let { return it }
        if (classes.matchesAnyMarker(RichTextParserConfig.centerAlignmentClassMarkers)) return TextAlign.Center
        if (classes.matchesAnyMarker(RichTextParserConfig.endAlignmentClassMarkers)) return TextAlign.End
        return TextAlign.Start
    }

    private fun KsoupElement.isHeaderCellCandidate(classNames: Set<String>): Boolean {
        if (tagName.equals("th", ignoreCase = true)) return true
        if (classNames.matchesAnyMarker(RichTextParserConfig.headerCellClassMarkers)) return true
        if (hasHeaderAttributeMarker()) return true
        val scope = attr("scope")
        if (scope.equals("col", true) || scope.equals("colgroup", true) ||
            scope.equals("row", true) || scope.equals("rowgroup", true)) return true
        val role = attr("role")
        if (role.equals("columnheader", true) || role.equals("rowheader", true)) return true
        return false
    }

    private fun KsoupElement.hasHeaderAttributeMarker(): Boolean {
        RichTextParserConfig.headerRowAttributeNames.forEach { attrName ->
            val value = attr(attrName)
            if (value.isBlank()) return@forEach
            if (attrName.equals("scope", true)) {
                if (value.equals("col", true) || value.equals("colgroup", true) ||
                    value.equals("row", true) || value.equals("rowgroup", true)) {
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
                if (attrName.contains("title", true) || attrName.contains("caption", true) ||
                    attrName.contains("heading", true)) {
                    if (normalizedValue.isNotEmpty()) return true
                }
                if (normalizedValue.equals("true", true) || normalizedValue.equals("1") ||
                    normalizedValue.equals("yes", true)) return true
            }
            if (RichTextParserConfig.headerAttributeValues.any { candidate ->
                normalizedValue.contains(candidate, ignoreCase = true)
            }) {
                return true
            }
        }
        return false
    }

    // ==================== OTHER BLOCK PARSERS ====================

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
}

/**
 * Simple logger for RichTextParser debugging.
 */
internal object RichTextParserLogger {
    var isEnabled: Boolean = false

    fun log(message: String) {
        if (isEnabled) {
            println("RichTextParser: $message")
        }
    }
}
