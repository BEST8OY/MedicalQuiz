package com.medicalquiz.app.shared.data.models

import kotlinx.serialization.Serializable

/**
 * Represents a text highlight within question content.
 * Highlights are stored per database and question, allowing multiple
 * highlighted ranges per question across question text and explanation.
 *
 * The [highlightedText] field stores the actual text at the time of creation
 * for validation on re-parse (e.g., if the source HTML changes).
 */
@Serializable
data class TextHighlight(
    val id: Long = 0,
    val dbName: String,
    val questionId: Long,
    val section: HighlightSection,
    val startOffset: Int,
    val endOffset: Int,
    val highlightedText: String,
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

@Serializable
enum class HighlightSection {
    /** Main question text */
    QUESTION,
    /** Explanation/rationale text */
    EXPLANATION
}

/**
 * Predefined highlight colors for text highlighting.
 * Persisted as enum names ("YELLOW", "GREEN", ...) in SQLite.
 * Visual colors are resolved at the UI layer via HighlightTheme / toContainerColors.
 */
@Serializable
enum class HighlightColor(val displayName: String) {
    YELLOW("Yellow"),
    GREEN("Green"),
    BLUE("Blue"),
    PINK("Pink"),
    ORANGE("Orange");

    companion object {
        fun fromName(name: String): HighlightColor {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: YELLOW
        }
    }
}
