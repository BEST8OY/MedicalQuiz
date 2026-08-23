package com.medqb.app.shared.ui.state

import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.Question
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.data.models.TextHighlight

/**
 * UI State for the active Quiz session.
 */
data class QuizUiState(
    val databaseName: String = "",
    val entryName: String = "",
    val questionIds: List<Long> = emptyList(),
    val currentQuestionIndex: Int = -1,
    val currentQuestion: Question? = null,
    val currentAnswers: List<Answer> = emptyList(),
    // Derived once when the question loads (see QuestionDetails) — single source of
    // truth shared by answer grading UI and answer logging.
    val correctAnswerId: Int? = null,
    // Saved highlights for the displayed question. Loaded with the question payload
    // (see LoadQuestionUseCase) so text and highlights render in the same frame.
    val questionHighlights: List<TextHighlight> = emptyList(),
    val explanationHighlights: List<TextHighlight> = emptyList(),
    val selectedAnswerId: Int? = null,
    val answerSubmitted: Boolean = false,
    val isLoading: Boolean = false,
    
    val previewQuestionCount: Int = 0,
    val currentPerformance: QuestionPerformance? = null,
    val isLoggingEnabled: Boolean = false,
    val submissionMode: SubmissionMode = SubmissionMode.INSTANT,
    val showMetadata: Boolean = true,
    val autoLoadFirstQuestion: Boolean = false,
    val fontScalePreference: Float? = null,
) {
    val totalQuestions: Int
        get() = questionIds.size

    val hasPreviousQuestion: Boolean
        get() = currentQuestionIndex > 0

    val hasNextQuestion: Boolean
        get() = currentQuestionIndex < questionIds.size - 1

    companion object {
        val EMPTY = QuizUiState()
    }

    fun copyWithQuestion(
        question: Question?,
        answers: List<Answer>,
        correctAnswerId: Int?,
        questionHighlights: List<TextHighlight>,
        explanationHighlights: List<TextHighlight>,
        resetAnswerState: Boolean
    ): QuizUiState {
        return copy(
            currentQuestion = question,
            currentAnswers = answers,
            correctAnswerId = correctAnswerId,
            // Highlights always swap with the question — they belong to it.
            questionHighlights = questionHighlights,
            explanationHighlights = explanationHighlights,
            selectedAnswerId = if (resetAnswerState) null else selectedAnswerId,
            answerSubmitted = if (resetAnswerState) false else answerSubmitted
        )
    }
}
