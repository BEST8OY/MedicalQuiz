package com.medqb.app.shared.data

import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight

/**
 * Persistence gateway for text highlights, scoped explicitly by `(dbName, questionId)`.
 *
 * The repository is deliberately stateless: it holds no "currently displayed question"
 * context and publishes no flows. Question loads fetch highlights through
 * [getHighlightsForQuestion] and fold them into the quiz UI state atomically;
 * mutations return the refreshed highlight list for the question so callers can
 * publish a consistent snapshot.
 */
interface TextHighlightsRepository {
    /** All highlights for the question, across both sections. */
    suspend fun getHighlightsForQuestion(dbName: String, questionId: Long): List<TextHighlight>

    /**
     * Adds [highlightedText] as a highlight, merging any strictly-overlapping highlights
     * in [section]. Returns the refreshed full highlight list for the question.
     */
    suspend fun addHighlight(
        dbName: String,
        questionId: Long,
        section: HighlightSection,
        startOffset: Int,
        endOffset: Int,
        highlightedText: String,
        color: HighlightColor = HighlightColor.YELLOW,
    ): List<TextHighlight>

    /** Deletes one highlight by id. Returns the refreshed list for the question. */
    suspend fun removeHighlight(
        dbName: String,
        questionId: Long,
        highlightId: Long,
    ): List<TextHighlight>

    /** Changes a highlight's color. Returns the refreshed list for the question. */
    suspend fun updateHighlightColor(
        dbName: String,
        questionId: Long,
        highlightId: Long,
        color: HighlightColor,
    ): List<TextHighlight>
}
