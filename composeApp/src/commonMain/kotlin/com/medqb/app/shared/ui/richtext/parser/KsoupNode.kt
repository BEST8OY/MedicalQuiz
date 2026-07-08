package com.medqb.app.shared.ui.richtext.parser

import com.medqb.app.shared.ui.richtext.matchesAnyMarker

/**
 * Sealed interface representing nodes in the HTML DOM tree.
 */
internal sealed interface KsoupNode {
    /** The parent element, or null if this is a root node. */
    val parent: KsoupElement?
}

/**
 * Represents a text node in the HTML tree.
 *
 * @property text The text content
 * @property parent The parent element
 */
internal class KsoupTextNode(
    val text: String,
    override val parent: KsoupElement?
) : KsoupNode

/**
 * Represents an HTML element in the DOM tree.
 *
 * @property tagName The HTML tag name (e.g., "div", "p")
 * @property attributes Map of attribute names to values
 * @property parent The parent element
 */
internal class KsoupElement(
    val tagName: String,
    val attributes: Map<String, String>,
    override val parent: KsoupElement?
) : KsoupNode {

    /** Child nodes of this element. */
    val children = mutableListOf<KsoupNode>()

    /** Cached CSS class names parsed from the class attribute. */
    private val _classNames: Set<String> by lazy {
        attributes["class"]?.split(" ")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    /** Cache for ancestor class lookups. */
    private val ancestorClassCache = mutableMapOf<KsoupElement?, Set<String>>()

    /** Cache for bold content checks. */
    private val boldContentCache = mutableMapOf<Int, Boolean>()

    /**
     * Gets an attribute value by name.
     *
     * @param name The attribute name
     * @return The attribute value or empty string if not present
     */
    fun attr(name: String): String = attributes[name] ?: ""

    /**
     * Case-insensitive attribute lookup.
     * Handles parsers that may store attribute names in non-lowercase form.
     */
    fun attrIgnoreCase(name: String): String {
        val lower = name.lowercase()
        return attributes.entries.firstOrNull { it.key.lowercase() == lower }?.value ?: ""
    }

    /**
     * Checks if an attribute exists.
     *
     * @param name The attribute name
     * @return True if the attribute exists
     */
    fun hasAttr(name: String): Boolean = attributes.containsKey(name)

    /**
     * Gets the set of CSS class names applied to this element.
     *
     * @return Set of class names
     */
    fun classNames(): Set<String> = _classNames

    /**
     * Collects all text content from this element and its descendants.
     *
     * @return The combined text content
     */
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

    /**
     * Gets all CSS class names from ancestors up to (but not including) the specified element.
     *
     * @param stopAt The element to stop at (exclusive)
     * @return Set of all ancestor class names
     */
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

    /**
     * Checks if this element or its descendants contain bold content.
     *
     * @param maxDepth Maximum recursion depth to check
     * @return True if bold content is found
     */
    fun containsBoldContent(maxDepth: Int = RichTextParserConfig.BOLD_CHECK_MAX_DEPTH): Boolean {
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
