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

// Parsing limits to prevent resource exhaustion
private const val MAX_RECURSION_DEPTH = 100
private const val MAX_TABLE_ROWS = 1000
private const val MAX_TABLE_COLUMNS = 50

// Heuristic thresholds
private const val MAX_TITLE_LENGTH = 200
private const val BOLD_FONT_WEIGHT_THRESHOLD = 600
private const val BOLD_CHECK_MAX_DEPTH = 4
private const val EM_TO_DP_MULTIPLIER = 16f
private const val ALIGNMENT_DESCENT_MAX_DEPTH = 3

// ============================================================================
// CSS Parsing Utilities
// ============================================================================

private object CssParser {
    fun extractValue(styleAttr: String, property: String): String? {
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

    fun isBoldStyle(styleAttr: String): Boolean {
        if (styleAttr.isBlank()) return false
        val fontWeight = extractValue(styleAttr, "font-weight")?.lowercase()?.trim() ?: return false
        if (fontWeight.startsWith("bold") || fontWeight.startsWith("bolder")) return true
        val numeric = fontWeight.filter { it.isDigit() }
        return numeric.toIntOrNull()?.let { it >= BOLD_FONT_WEIGHT_THRESHOLD } == true
    }

    fun parseDimension(value: String): Float? {
        val clean = value.trim().lowercase()
        return when {
            clean.endsWith("%") -> null // Ignore percentages
            clean.endsWith("px") -> clean.removeSuffix("px").toFloatOrNull()
            else -> clean.toFloatOrNull()
        }
    }

    fun parsePaddingStart(styleAttr: String): Dp {
        val padding = extractValue(styleAttr, "padding-left") ?: return 0.dp
        return when {
            padding.endsWith("em") -> {
                val value = padding.removeSuffix("em").toFloatOrNull() ?: 0f
                (value * EM_TO_DP_MULTIPLIER).dp
            }
            padding.endsWith("px") -> {
                val value = padding.removeSuffix("px").toFloatOrNull() ?: 0f
                value.dp
            }
            else -> 0.dp
        }
    }

    fun parseWidth(widthAttr: String, styleAttr: String): Float? {
        extractValue(styleAttr, "width")?.let { parseDimension(it)?.let { w -> return w } }
        extractValue(styleAttr, "min-width")?.let { parseDimension(it)?.let { w -> return w } }
        extractValue(styleAttr, "max-width")?.let { parseDimension(it)?.let { w -> return w } }
        if (widthAttr.isNotBlank()) {
            parseDimension(widthAttr)?.let { return it }
        }
        return null
    }

    fun parseTextAlign(alignAttr: String, styleAttr: String): TextAlign? {
        if (alignAttr.isNotEmpty()) {
            return when (alignAttr.lowercase()) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                "justify" -> TextAlign.Justify
                else -> TextAlign.Start
            }
        }
        val style = styleAttr.lowercase()
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
}

/** Configuration container for parser tag and attribute metadata. */
private object RichTextParserConfig {
    val tooltipAttributeCandidates = setOf(
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

    val tooltipContentClassNames = setOf(
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

    val headerRowClassMarkers = normalizedMarkers(
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

    val titleRowClassMarkers = normalizedMarkers(
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

    val headerCellClassMarkers = normalizedMarkers(
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

    val boldClassMarkers = normalizedMarkers(
        "bold",
        "text-bold",
        "fw-bold",
        "fwbold",
        "font-weight-bold",
        "fontweightbold",
        "strong",
        "important"
    )

    val centerAlignmentClassMarkers = normalizedMarkers(
        "text-center",
        "text-centre",
        "align-center",
        "centered",
        "centre-text",
        "ta-center",
        "tacentre",
        "center-text"
    )

    val endAlignmentClassMarkers = normalizedMarkers(
        "text-right",
        "text-end",
        "align-right",
        "align-end",
        "ta-right",
        "taright",
        "text-right-align",
        "textright"
    )

    val headerAttributeValues = setOf(
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

    val headerRowAttributeNames = setOf(
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

    val headerCellAttributeNames = setOf(
        "data-cell-type",
        "data-type",
        "role",
        "data-role",
        "data-header",
        "data-heading",
        "headers",
        "scope"
    )

    val blockLevelChildTags = setOf(
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
        "hr",
        "svg",
        "canvas",
        "iframe",
        "object"
    )
}

internal object RichTextParserLogger {
    var isEnabled: Boolean = false

    fun log(message: String) {
        if (isEnabled) {
            println("RichTextParser: $message")
        }
    }
}

private val namedHtmlEntities = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to "\u00A0",
    "ndash" to "–",
    "mdash" to "—",
    "lsquo" to "'",
    "rsquo" to "'",
    "ldquo" to "\"",
    "rdquo" to "\"",
    "hellip" to "…",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
    "euro" to "€",
    "pound" to "£",
    "yen" to "¥",
    "cent" to "¢",
    "deg" to "°",
    "plusmn" to "±",
    "times" to "×",
    "divide" to "÷",
    "frac12" to "½",
    "frac14" to "¼",
    "frac34" to "¾"
)

private val htmlEntityPattern = Regex("&(#x?[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

private object StringBuilderPool {
    private const val MAX_POOL_SIZE = 8
    private val pool = ArrayDeque<StringBuilder>(MAX_POOL_SIZE)

    fun obtain(): StringBuilder = synchronized(pool) {
        pool.removeLastOrNull()?.apply { setLength(0) } ?: StringBuilder()
    }

    fun recycle(builder: StringBuilder) {
        builder.setLength(0)
        synchronized(pool) {
            if (pool.size < MAX_POOL_SIZE) {
                pool.addLast(builder)
            }
        }
    }
}

private fun codePointToString(codePoint: Int): String {
    if (codePoint in 1..0xFFFF) {
        return codePoint.toChar().toString()
    }
    if (codePoint in 0x10000..0x10FFFF) {
        val high = ((codePoint - 0x10000) shr 10) + 0xD800
        val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
        return charArrayOf(high.toChar(), low.toChar()).concatToString()
    }
    return ""
}

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
    return htmlEntityPattern.replace(text) { matchResult ->
        val body = matchResult.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) -> {
                val codePoint = body.substring(2).toIntOrNull(16)
                codePoint?.let { codePointToString(it).ifEmpty { matchResult.value } } ?: matchResult.value
            }
            body.startsWith("#") -> {
                val codePoint = body.substring(1).toIntOrNull()
                codePoint?.let { codePointToString(it).ifEmpty { matchResult.value } } ?: matchResult.value
            }
            else -> namedHtmlEntities[body] ?: matchResult.value
        }
    }
}

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
    private val ancestorClassCache = mutableMapOf<KsoupElement?, Set<String>>()
    private val boldContentCache = mutableMapOf<Int, Boolean>()

    fun attr(name: String): String = attributes[name] ?: ""
    fun hasAttr(name: String): Boolean = attributes.containsKey(name)
    fun classNames(): Set<String> = attributes["class"]?.split(" ")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun text(): String {
        val sb = StringBuilderPool.obtain()
        collectText(this, sb)
        val text = sb.toString()
        StringBuilderPool.recycle(sb)
        return text
    }

    private fun collectText(node: KsoupNode, sb: StringBuilder) {
        when (node) {
            is KsoupTextNode -> sb.append(node.text)
            is KsoupElement -> node.children.forEach { collectText(it, sb) }
        }
    }

    fun ancestorClasses(stopAt: KsoupElement?): Set<String> {
        return ancestorClassCache.getOrPut(stopAt) {
            val classes = LinkedHashSet<String>()
            var cursor = parent
            while (cursor != null && cursor != stopAt) {
                cursor.classNames().forEach { if (it.isNotBlank()) classes += it }
                cursor = cursor.parent
            }
            classes
        }
    }

    fun containsBoldContent(maxDepth: Int = BOLD_CHECK_MAX_DEPTH): Boolean {
        return boldContentCache.getOrPut(maxDepth) {
            when {
                maxDepth <= 0 -> false
                tagName.equals("strong", true) || tagName.equals("b", true) -> true
                CssParser.isBoldStyle(attr("style")) -> true
                classNames().matchesAnyMarker(RichTextParserConfig.boldClassMarkers) -> true
                else -> {
                    children.any { child ->
                        child is KsoupElement && child.containsBoldContent(maxDepth - 1)
                    }
                }
            }
        }
    }
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
        if (depth >= MAX_RECURSION_DEPTH) {
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
        return RichTextParserConfig.blockLevelChildTags.contains(tag)
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
        if (allRows.size > MAX_TABLE_ROWS) {
            println("RichText: Table has ${allRows.size} rows, limiting to $MAX_TABLE_ROWS")
            while (allRows.size > MAX_TABLE_ROWS) {
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
        if (columnCount > MAX_TABLE_COLUMNS) {
            println("RichText: Table has $columnCount columns, limiting to $MAX_TABLE_COLUMNS")
            columnCount = MAX_TABLE_COLUMNS
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
                row.hasHeaderAttributeMarker(RichTextParserConfig.headerRowAttributeNames)
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
        if (row.hasHeaderAttributeMarker(RichTextParserConfig.headerRowAttributeNames)) return true
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
            val isMeaningfulTitle = textLength in 1..MAX_TITLE_LENGTH && alphanumericCount > 0 && alphanumericCount * 2 >= textLength
            if (isMeaningfulTitle) {
                val rowAlignment = parseTextAlign(row)
                val centerAligned = info.alignment == TextAlign.Center || rowAlignment == TextAlign.Center
                val spansMultiple = info.columnSpan >= 2
                val rowHasTitleClass = row.classNames().matchesAnyMarker(RichTextParserConfig.titleRowClassMarkers)
                val rowHasHeaderAttrs = row.hasHeaderAttributeMarker(RichTextParserConfig.headerRowAttributeNames)
                val emphasised = info.hasHeaderTraits || rowHasTitleClass || rowHasHeaderAttrs
                
                return (centerAligned || emphasised) && isFirstRow
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
        remainingDepth: Int = ALIGNMENT_DESCENT_MAX_DEPTH
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
        if (hasHeaderAttributeMarker(RichTextParserConfig.headerCellAttributeNames)) return true
        val scope = attr("scope")
        if (scope.equals("col", true) || scope.equals("colgroup", true) || scope.equals("row", true) || scope.equals("rowgroup", true)) return true
        val role = attr("role")
        if (role.equals("columnheader", true) || role.equals("rowheader", true)) return true
        return false
    }

    private fun KsoupElement.hasHeaderAttributeMarker(attributeNames: Set<String>): Boolean {
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
            if (RichTextParserConfig.headerAttributeValues.any { candidate -> normalizedValue.contains(candidate, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    private fun styleIndicatesBold(styleAttr: String): Boolean = CssParser.isBoldStyle(styleAttr)
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
        if (classNames().containsAnyInsensitive(RichTextParserConfig.tooltipContentClassNames)) return true
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
        RichTextParserConfig.tooltipAttributeCandidates.forEach { attrName ->
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

private class InlineStyleBuilder(initial: InlineStyle = InlineStyle()) {
    var bold: Boolean = initial.bold
    var italic: Boolean = initial.italic
    var underline: Boolean = initial.underline
    var monospace: Boolean = initial.monospace
    var superscript: Boolean = initial.superscript
    var subscript: Boolean = initial.subscript
    var link: String? = initial.link
    var highlight: InlineHighlight? = initial.highlight
    var dictionary: Boolean = initial.dictionary
    var preserveWhitespace: Boolean = initial.preserveWhitespace
    var smallText: Boolean = initial.smallText
    var textColor: Color? = initial.textColor
    var tooltip: String? = initial.tooltip

    fun build(): InlineStyle = InlineStyle(
        bold = bold,
        italic = italic,
        underline = underline,
        monospace = monospace,
        superscript = superscript,
        subscript = subscript,
        link = link,
        highlight = highlight,
        dictionary = dictionary,
        preserveWhitespace = preserveWhitespace,
        smallText = smallText,
        textColor = textColor,
        tooltip = tooltip
    )
}

private fun InlineStyle.applyClassStyles(
    classes: Set<String>,
    palette: RichTextPalette,
    showSelectedHighlight: Boolean
): InlineStyle {
    if (classes.isEmpty()) return this
    val builder = InlineStyleBuilder(this)
    classes.forEach { rawClass ->
        when (rawClass.lowercase()) {
            "important", "wichtig" -> {
                builder.highlight = InlineHighlight.IMPORTANT
                builder.bold = true
            }
            "selected" -> if (showSelectedHighlight) {
                builder.highlight = InlineHighlight.SELECTED
            }
            "dictionary" -> {
                builder.dictionary = true
                builder.underline = true
            }
            "nowrap" -> builder.preserveWhitespace = true
            "scientific-name" -> builder.italic = true
            "abstract" -> {
                builder.smallText = true
                builder.textColor = palette.abstractText
            }
            "metalink" -> {
                builder.textColor = Color(0xFFE91E63)
                builder.italic = true
            }
        }
    }
    return builder.build()
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

