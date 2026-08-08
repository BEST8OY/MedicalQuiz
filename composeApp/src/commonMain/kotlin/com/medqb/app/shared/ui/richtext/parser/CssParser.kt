package com.medqb.app.shared.ui.richtext.parser

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CSS parsing utilities for extracting style information from HTML attributes.
 */
internal object CssParser {
    private const val EM_TO_DP_MULTIPLIER = 16f
    private const val BOLD_FONT_WEIGHT_THRESHOLD = 600

    /**
     * Extracts a CSS property value from a style attribute string.
     *
     * @param styleAttr The style attribute value (e.g., "color: red; font-size: 12px")
     * @param property The property name to extract (e.g., "color")
     * @return The property value or null if not found
     */
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

    /**
     * Checks if a style attribute indicates bold font weight.
     *
     * @param styleAttr The style attribute string
     * @return True if the style indicates bold text
     */
    fun isBoldStyle(styleAttr: String): Boolean {
        if (styleAttr.isBlank()) return false
        val fontWeight = extractValue(styleAttr, "font-weight")?.lowercase()?.trim() ?: return false
        if (fontWeight.startsWith("bold") || fontWeight.startsWith("bolder")) return true
        val numeric = fontWeight.filter { it.isDigit() }
        return numeric.toIntOrNull()?.let { it >= BOLD_FONT_WEIGHT_THRESHOLD } == true
    }

    /**
     * Parses a dimension value (px, em, etc.) into a float.
     *
     * @param value The dimension string (e.g., "12px", "1.5em")
     * @return The numeric value or null if not parseable
     */
    fun parseDimension(value: String): Float? {
        val clean = value.trim().lowercase()
        return when {
            clean.endsWith("%") -> null // Ignore percentages
            clean.endsWith("px") -> clean.removeSuffix("px").toFloatOrNull()
            else -> clean.toFloatOrNull()
        }
    }

    /**
     * Parses left padding from a style attribute into Dp.
     *
     * @param styleAttr The style attribute string
     * @return The padding value in Dp
     */
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

    /**
     * Parses width from width attribute or style.
     *
     * @param widthAttr The width attribute value
     * @param styleAttr The style attribute value
     * @return The width as a float or null
     */
    fun parseWidth(widthAttr: String, styleAttr: String): Float? {
        extractValue(styleAttr, "width")?.let { parseDimension(it)?.let { w -> return w } }
        extractValue(styleAttr, "min-width")?.let { parseDimension(it)?.let { w -> return w } }
        extractValue(styleAttr, "max-width")?.let { parseDimension(it)?.let { w -> return w } }
        if (widthAttr.isNotBlank()) {
            parseDimension(widthAttr)?.let { return it }
        }
        return null
    }

    /**
     * Parses text alignment from align attribute or style.
     *
     * @param alignAttr The align attribute value
     * @param styleAttr The style attribute value
     * @return The TextAlign value or null
     */
    fun parseTextAlign(alignAttr: String, styleAttr: String): TextAlign? {
        if (alignAttr.isNotEmpty()) {
            return when (alignAttr.lowercase()) {
                "center" -> TextAlign.Center
                "right", "end" -> TextAlign.End
                "justify" -> TextAlign.Justify
                "left", "start" -> TextAlign.Start
                else -> null // Unknown value: treat as absent per HTML spec
            }
        }
        val style = styleAttr.lowercase()
        if (style.contains("text-align")) {
            val value = style.substringAfter("text-align").substringAfter(":").substringBefore(";").trim()
            return when (value) {
                "center" -> TextAlign.Center
                "right", "end" -> TextAlign.End
                "justify" -> TextAlign.Justify
                "left", "start" -> TextAlign.Start
                else -> null // Unknown value: treat as absent per CSS spec
            }
        }
        return null
    }
}
