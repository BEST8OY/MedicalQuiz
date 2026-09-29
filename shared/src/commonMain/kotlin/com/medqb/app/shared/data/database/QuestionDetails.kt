package com.medqb.app.shared.data.database

import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.Question
import com.medqb.app.shared.data.models.TextHighlight

/**
 * Fully-resolved question payload for the quiz screen.
 *
 * [correctAnswerId] is derived here — once, from the deterministic answer ordering
 * (`ORDER BY id`) and [Question.corrAns] (1-based index) — so UI and logging layers
 * share a single source of truth instead of each re-deriving it.
 *
 * Highlights travel with the payload so text and its highlights are published in a
 * single atomic state transition — they can never desynchronize across frames.
 */
data class QuestionDetails(
    val question: Question?,
    val answers: List<Answer>,
    val performance: QuestionPerformance?,
    val correctAnswerId: Int?,
    val questionHighlights: List<TextHighlight> = emptyList(),
    val explanationHighlights: List<TextHighlight> = emptyList(),
) {
    companion object {
        fun from(
            question: Question?,
            answers: List<Answer>,
            performance: QuestionPerformance?
        ): QuestionDetails {
            val correctAnswerId = question
                ?.takeIf { it.corrAns > 0 }
                ?.let { answers.getOrNull(it.corrAns - 1)?.answerId?.toInt() }
            return QuestionDetails(question, answers, performance, correctAnswerId)
        }
    }
}
