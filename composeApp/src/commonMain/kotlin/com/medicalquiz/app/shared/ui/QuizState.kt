package com.medicalquiz.app.shared.ui

import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.database.QuestionPerformance
import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.data.models.Question
import com.medicalquiz.app.shared.data.models.Subject
import com.medicalquiz.app.shared.data.models.System
import com.medicalquiz.app.shared.utils.Resource

data class QuizState(
    val databaseName: String = "",
    val questionIds: List<Long> = emptyList(),
    val currentQuestionIndex: Int = -1,
    val currentQuestion: Question? = null,
    val currentAnswers: List<Answer> = emptyList(),
    val selectedAnswerId: Int? = null,
    val answerSubmitted: Boolean = false,
    val isLoading: Boolean = false,
    val selectedSubjectIds: Set<Long> = emptySet(),
    val selectedSystemIds: Set<Long> = emptySet(),
    val performanceFilter: PerformanceFilter = PerformanceFilter.ALL,
    val subjectsResource: Resource<List<Subject>> = Resource.Success(emptyList()),
    val systemsResource: Resource<List<System>> = Resource.Success(emptyList()),
    val previewQuestionCount: Int = 0,
    val currentPerformance: QuestionPerformance? = null,
    val isLoggingEnabled: Boolean = true,
    val autoLoadFirstQuestion: Boolean = false
) {
    companion object {
        val EMPTY = QuizState()
    }

    fun copyWithQuestion(
        question: Question?,
        answers: List<Answer>,
        resetAnswerState: Boolean
    ): QuizState {
        return copy(
            currentQuestion = question,
            currentAnswers = answers,
            selectedAnswerId = if (resetAnswerState) null else selectedAnswerId,
            answerSubmitted = if (resetAnswerState) false else answerSubmitted
        )
    }
}
