package com.medicalquiz.app.shared.ui.richtext.parser

import com.medicalquiz.app.shared.ui.richtext.containsAnyInsensitive
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses tooltip text from various HTML attribute sources.
 */
internal object TooltipParser {

    /**
     * Attribute candidates that may contain tooltip text.
     */
    private val tooltipAttributeCandidates = setOf(
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

    /**
     * CSS class names that indicate tooltip content nodes.
     */
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

    /**
     * Extracts tooltip text from an element, checking various attributes and inline content.
     *
     * @param element The element to extract tooltip from
     * @return The tooltip text or null if not found
     */
    fun extractTooltipText(element: KsoupElement): String? {
        // Check tooltip attributes
        tooltipAttributeCandidates.forEach { attrName ->
            val value = element.attr(attrName)
            if (value.isNotBlank()) {
                parseTooltipPayload(value)?.let { return it }
            }
        }

        // Check JSON tooltip attribute
        element.attr("data-tooltip-json").takeIf { it.isNotBlank() }?.let { candidate ->
            parseTooltipPayload(candidate)?.let { return it }
        }

        // Check for inline tooltip node
        val inlineNode = findInlineTooltipNode(element)
        val inlineText = inlineNode?.text()?.trim()
        return inlineText?.takeIf { it.isNotEmpty() }
    }

    /**
     * Checks if an element is a tooltip content node.
     *
     * @param element The element to check
     * @return True if this is a tooltip content node
     */
    fun isTooltipContentNode(element: KsoupElement): Boolean {
        if (element.classNames().containsAnyInsensitive(tooltipContentClassNames)) return true
        if (element.attr("data-role").equals("tooltip", true)) return true
        if (element.attr("data-tooltip-part").equals("content", true)) return true
        if (element.hasAttr("data-tooltip-content") || element.hasAttr("data-tooltip-text")) return true
        if (element.attr("data-tooltip-role").equals("content", true)) return true
        if (element.attr("data-type").equals("tooltip", true)) return true
        return false
    }

    private fun findInlineTooltipNode(element: KsoupElement): KsoupElement? {
        if (isTooltipContentNode(element)) return element
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
            // Try parsing as JSON object
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = Json.parseToJsonElement(trimmed).jsonObject
                val keys = listOf("description", "text", "content", "value", "body", "tooltip", "message")
                for (key in keys) {
                    val value = json[key]?.jsonPrimitive?.contentOrNull
                    if (!value.isNullOrBlank()) {
                        return stripHtml(value)
                    }
                }
            }
            // Try parsing as JSON array
            else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val array = Json.parseToJsonElement(trimmed).jsonArray
                for (element in array) {
                    val candidate = if (element is kotlinx.serialization.json.JsonObject) {
                        val keys = listOf("description", "text", "content", "value", "body", "tooltip", "message")
                        keys.firstNotNullOfOrNull { key ->
                            element[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        }
                    } else {
                        element.jsonPrimitive.contentOrNull
                    }
                    if (!candidate.isNullOrBlank()) {
                        return stripHtml(candidate)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore JSON parsing errors
        }

        // Return as plain text after stripping HTML
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
