package com.medicalquiz.app.shared.data.models

import kotlinx.serialization.Serializable

/**
 * Represents a text highlight within question content.
 * Highlights are stored per database and question, allowing multiple
 * highlighted ranges per question across question text and explanation.
 */
@Serializable
data class TextHighlight(
    val id: Long = 0,
    val dbName: String,
    val questionId: Long,
    val section: HighlightSection,
    val startOffset: Int,
    val endOffset: Int,
    val highlightedText: String, // Store the actual text for validation on re-parse
    val color: HighlightColor = HighlightColor.YELLOW,
    val createdAt: Long
) {
    /**
     * Check if this highlight overlaps with another range.
     */
    fun overlaps(start: Int, end: Int): Boolean {
        return startOffset < end && endOffset > start
    }

    /**
     * Check if this highlight contains a specific offset.
     */
    fun contains(offset: Int): Boolean {
        return offset in startOffset until endOffset
    }
}

/**
 * Sections of a question that can be highlighted.
 */
@Serializable
enum class HighlightSection {
    QUESTION,    // Main question text
    EXPLANATION  // Explanation/rationale text
}

/**
 * Predefined highlight colors for text highlighting.
 */
@Serializable
enum class HighlightColor(val hex: String, val displayName: String) {
    YELLOW("#FFEB3B", "Yellow"),
    GREEN("#4CAF50", "Green"),
    BLUE("#2196F3", "Blue"),
    PINK("#E91E63", "Pink"),
    ORANGE("#FF9800", "Orange");

    companion object {
        fun fromHex(hex: String): HighlightColor {
            return entries.find { it.hex.equals(hex, ignoreCase = true) } ?: YELLOW
        }

        fun fromName(name: String): HighlightColor {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: YELLOW
        }
    }
}
