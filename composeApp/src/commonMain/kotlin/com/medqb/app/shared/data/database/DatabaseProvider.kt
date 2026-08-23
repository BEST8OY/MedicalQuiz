package com.medqb.app.shared.data.database

import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.Question
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.System

/**
 * A single question-bank database.
 *
 * Implementations own their database name — callers never pass one, so the
 * connection and its identity can never disagree (e.g. across a db switch).
 */
interface DatabaseProvider {
    suspend fun closeDatabase()

    suspend fun getQuestionIds(
        subjectIds: List<Long>? = null,
        systemIds: List<Long>? = null,
        performanceFilter: PerformanceFilter = PerformanceFilter.ALL
    ): List<Long>

    suspend fun getQuestionById(id: Long): Question?
    suspend fun getAnswersForQuestion(questionId: Long): List<Answer>
    suspend fun getQuestionWithDetails(
        questionId: Long,
        loadPerformance: Boolean,
    ): QuestionDetails
    suspend fun countQuestionIds(
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter,
    ): Int

    suspend fun getSubjects(): List<Subject>
    suspend fun getSystems(subjectIds: List<Long>? = null): List<System>

    suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        sessionId: String
    )

    suspend fun clearLogForQuestion(qid: Long)
    suspend fun getQuestionPerformance(qid: Long): QuestionPerformance?
}
