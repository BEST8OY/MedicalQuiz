package com.medicalquiz.app.ui

import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question

/**
 * View state holder for quiz screen
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
    val hasNextQuestion: Boolean
        get() = currentQuestionIndex < questionIds.size - 1
    
    val hasPreviousQuestion: Boolean
        get() = currentQuestionIndex > 0
    
    val currentQuestionId: Long?
        get() = questionIds.getOrNull(currentQuestionIndex)

    val totalQuestions: Int
        get() = questionIds.size
}
