package com.medicalquiz.app.shared.ui.richtext.parser

/**
 * HTML entity decoding utilities.
 * Converts HTML entities (both named and numeric) to their character equivalents.
 */
internal object HtmlEntities {

    /**
     * Common named HTML entities and their character equivalents.
     */
    private val namedEntities = mapOf(
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

    /**
     * Regex pattern for matching HTML entities.
     * Matches: &name; &#123; &#x7B;
     */
    private val entityPattern = Regex("&(#x?[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    /**
     * Decodes HTML entities in text to their character equivalents.
     *
     * Handles:
     * - Named entities: &amp;, &lt;, &gt;, etc.
     * - Decimal numeric: &#39; → '
     * - Hexadecimal numeric: &#x27; → '
     *
     * @param text The text containing HTML entities
     * @return Text with entities decoded to characters
     */
    fun decode(text: String): String {
        if (!text.contains('&')) return text
        return entityPattern.replace(text) { matchResult ->
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
                else -> namedEntities[body] ?: matchResult.value
            }
        }
    }

    /**
     * Converts a Unicode code point to a String.
     * Handles both BMP (Basic Multilingual Plane) and supplementary characters.
     *
     * @param codePoint The Unicode code point
     * @return The string representation or empty string if invalid
     */
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
}
