package com.medqb.app.shared.ui.richtext.parser

/**
 * HTML entity decoding utilities.
 * Converts HTML entities (both named and numeric) to their character equivalents.
 */
internal object HtmlEntities {

    /**
     * Common named HTML entities and their character equivalents.
     */
    private val namedEntities = mapOf(
        // Basic
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to "\u00A0",
        // Punctuation
        "ndash" to "–",
        "mdash" to "—",
        "lsquo" to "'",
        "rsquo" to "'",
        "ldquo" to "\"",
        "rdquo" to "\"",
        "hellip" to "…",
        "laquo" to "«",
        "raquo" to "»",
        "iexcl" to "¡",
        "iquest" to "¿",
        "para" to "¶",
        "sect" to "§",
        "brvbar" to "¦",
        "uml" to "¨",
        "macr" to "¯",
        // Currency
        "euro" to "€",
        "pound" to "£",
        "yen" to "¥",
        "cent" to "¢",
        "curren" to "¤",
        // Math / symbols
        "deg" to "°",
        "plusmn" to "±",
        "times" to "×",
        "divide" to "÷",
        "micro" to "µ",
        "frac12" to "½",
        "frac14" to "¼",
        "frac34" to "¾",
        "sup1" to "¹",
        "sup2" to "²",
        "sup3" to "³",
        // Legal
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        // Arrows
        "larr" to "←",
        "rarr" to "→",
        "uarr" to "↑",
        "darr" to "↓",
        // Card suits
        "hearts" to "♥",
        "clubs" to "♣",
        "diams" to "♦",
        "spades" to "♠"
    )

    /**
     * Windows-1252 characters in the 0x80-0x9F range that are commonly
     * misused as HTML numeric entities. Browsers ignore these C1 control
     * code points and decode them to the Windows-1252 glyphs instead.
     */
    private val windows1252Overrides = mapOf(
        0x80 to '\u20AC',  // € Euro Sign
        0x82 to '\u201A',  // ‚ Single Low-9 Quotation Mark
        0x83 to '\u0192',  // ƒ Latin Small Letter F With Hook
        0x84 to '\u201E',  // „ Double Low-9 Quotation Mark
        0x85 to '\u2026',  // … Horizontal Ellipsis
        0x86 to '\u2020',  // † Dagger
        0x87 to '\u2021',  // ‡ Double Dagger
        0x88 to '\u02C6',  // ˆ Modifier Letter Circumflex Accent
        0x89 to '\u2030',  // ‰ Per Mille Sign
        0x8A to '\u0160',  // Š Latin Capital Letter S With Caron
        0x8B to '\u2039',  // ‹ Single Left-Pointing Angle Quotation Mark
        0x8C to '\u0152',  // Œ Latin Capital Ligature OE
        0x8E to '\u017D',  // Ž Latin Capital Letter Z With Caron
        0x91 to '\u2018',  // ' Left Single Quotation Mark
        0x92 to '\u2019',  // ' Right Single Quotation Mark
        0x93 to '\u201C',  // " Left Double Quotation Mark
        0x94 to '\u201D',  // " Right Double Quotation Mark
        0x95 to '\u2022',  // • Bullet
        0x96 to '\u2013',  // – En Dash
        0x97 to '\u2014',  // — Em Dash
        0x98 to '\u02DC',  // ˜ Small Tilde
        0x99 to '\u2122',  // ™ Trade Mark Sign
        0x9A to '\u0161',  // š Latin Small Letter S With Caron
        0x9B to '\u203A',  // › Single Right-Pointing Angle Quotation Mark
        0x9C to '\u0153',  // œ Latin Small Ligature OE
        0x9E to '\u017E',  // ž Latin Small Letter Z With Caron
        0x9F to '\u0178'   // Ÿ Latin Capital Letter Y With Diaeresis
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
                    codePoint?.let {
                        windows1252Overrides[it]?.toString()
                            ?: codePointToString(it).ifEmpty { matchResult.value }
                    } ?: matchResult.value
                }
                body.startsWith("#") -> {
                    val codePoint = body.substring(1).toIntOrNull()
                    codePoint?.let {
                        windows1252Overrides[it]?.toString()
                            ?: codePointToString(it).ifEmpty { matchResult.value }
                    } ?: matchResult.value
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

/**
 * Replaces Windows-1252 C1 control characters (U+0080–U+009F) with their
 * intended typographic equivalents. These characters appear when an HTML
 * parser decodes entities like &#x92; (which browsers interpret as Windows-1252
 * right single quotation mark, not the Unicode C1 control character).
 */
internal fun String.replaceWindows1252C1Controls(): String {
    if (this.isEmpty()) return this
    // Fast path: skip if no characters in the C1 range
    if (none { it.code in 0x80..0x9F }) return this
    val sb = StringBuilder(this.length)
    for (c in this) {
        val replacement = when (c.code) {
            0x80 -> "\u20AC"  // € Euro Sign
            0x82 -> "\u201A"  // ‚ Single Low-9 Quotation Mark
            0x83 -> "\u0192"  // ƒ Latin Small Letter F With Hook
            0x84 -> "\u201E"  // „ Double Low-9 Quotation Mark
            0x85 -> "\u2026"  // … Horizontal Ellipsis
            0x86 -> "\u2020"  // † Dagger
            0x87 -> "\u2021"  // ‡ Double Dagger
            0x88 -> "\u02C6"  // ˆ Modifier Letter Circumflex Accent
            0x89 -> "\u2030"  // ‰ Per Mille Sign
            0x8A -> "\u0160"  // Š Latin Capital Letter S With Caron
            0x8B -> "\u2039"  // ‹ Single Left-Pointing Angle Quotation Mark
            0x8C -> "\u0152"  // Œ Latin Capital Ligature OE
            0x8E -> "\u017D"  // Ž Latin Capital Letter Z With Caron
            0x91 -> "\u2018"  // ' Left Single Quotation Mark
            0x92 -> "\u2019"  // ' Right Single Quotation Mark
            0x93 -> "\u201C"  // " Left Double Quotation Mark
            0x94 -> "\u201D"  // " Right Double Quotation Mark
            0x95 -> "\u2022"  // • Bullet
            0x96 -> "\u2013"  // – En Dash
            0x97 -> "\u2014"  // — Em Dash
            0x98 -> "\u02DC"  // ˜ Small Tilde
            0x99 -> "\u2122"  // ™ Trade Mark Sign
            0x9A -> "\u0161"  // š Latin Small Letter S With Caron
            0x9B -> "\u203A"  // › Single Right-Pointing Angle Quotation Mark
            0x9C -> "\u0153"  // œ Latin Small Ligature OE
            0x9E -> "\u017E"  // ž Latin Small Letter Z With Caron
            0x9F -> "\u0178"  // Ÿ Latin Capital Letter Y With Diaeresis
            else -> null
        }
        if (replacement != null) sb.append(replacement) else sb.append(c)
    }
    return sb.toString()
}
