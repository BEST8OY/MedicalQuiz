package com.medicalquiz.app.ui

import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question

/**
 * View state holder for quiz screen with performance optimizations
 */
data class QuizState(
    val questionIds: List<Long> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val currentQuestion: Question? = null,
    val currentAnswers: List<Answer> = emptyList(),
    val selectedAnswerId: Int? = null,
    val answerSubmitted: Boolean = false,
    val selectedSubjectId: Long? = null,
    val selectedSystemId: Long? = null
) {
    // Cached computed properties to avoid repeated calculations
    val hasNextQuestion: Boolean by lazy { currentQuestionIndex < questionIds.size - 1 }
    val hasPreviousQuestion: Boolean by lazy { currentQuestionIndex > 0 }
    val currentQuestionId: Long? by lazy { questionIds.getOrNull(currentQuestionIndex) }
    val totalQuestions: Int by lazy { questionIds.size }

    // Performance-optimized validation
    val isValidState: Boolean by lazy {
        currentQuestionIndex >= 0 &&
        currentQuestionIndex < questionIds.size &&
        (currentQuestion == null || currentQuestion.id == currentQuestionId) &&
        currentAnswers.all { it.qId == currentQuestion?.id }
    }

    // Memory-efficient answer lookup
    fun getAnswerById(answerId: Long): Answer? = currentAnswers.find { it.answerId == answerId }

    // Pre-computed display strings to avoid repeated string building
    val questionNumberText: String by lazy { "Question ${currentQuestionIndex + 1} of $totalQuestions" }

    val metadataText: String by lazy {
        currentQuestion?.let { question ->
            buildString {
                append("ID: ${question.id}")
                question.subName?.let { append(" | Subject: $it") }
                question.sysName?.let { append(" | System: $it") }
            }
        } ?: ""
    }

    // Factory method for creating updated states efficiently
    fun copyWithQuestion(question: Question?, answers: List<Answer>): QuizState {
        return copy(
            currentQuestion = question,
            currentAnswers = answers,
            selectedAnswerId = null,
            answerSubmitted = false
        )
    }

    fun copyWithAnswerSelection(answerId: Int?): QuizState {
        return copy(selectedAnswerId = answerId)
    }

    fun copyWithAnswerSubmission(): QuizState {
        return copy(answerSubmitted = true)
    }

    companion object {
        val EMPTY = QuizState()
    }
}
