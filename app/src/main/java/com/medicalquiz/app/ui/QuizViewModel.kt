package com.medicalquiz.app.ui

import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.database.PerformanceFilter
import com.medicalquiz.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.medicalquiz.app.data.models.Question

/**
 * View state holder for quiz screen with performance optimizations
 */
data class QuizState(
    val questionIds: List<Long> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val currentQuestion: Question? = null,
    val currentAnswers: List<Answer> = emptyList(),
    val isLoading: Boolean = false,
    val selectedAnswerId: Int? = null,
    val answerSubmitted: Boolean = false,
    val currentPerformance: com.medicalquiz.app.data.database.QuestionPerformance? = null,
    val selectedSubjectIds: Set<Long> = emptySet(),
    val selectedSystemIds: Set<Long> = emptySet(),
    val performanceFilter: PerformanceFilter = PerformanceFilter.ALL,
    val isLoggingEnabled: Boolean = true,
    val subjectsResource: Resource<List<Subject>> = Resource.Success(emptyList()),
    val systemsResource: Resource<List<System>> = Resource.Success(emptyList())
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
