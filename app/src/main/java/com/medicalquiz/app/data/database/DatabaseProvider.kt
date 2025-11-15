package com.medicalquiz.app.data.database

import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.models.System

/**
 * Interface used by ViewModel to interact with the database.
 * Having this interface lets us test the ViewModel with a fake implementation.
 */
interface DatabaseProvider {
    suspend fun getQuestionIds(subjectIds: List<Long>? = null, systemIds: List<Long>? = null, performanceFilter: PerformanceFilter = PerformanceFilter.ALL): List<Long>
    suspend fun getQuestionById(id: Long): Question?
    suspend fun getAnswersForQuestion(questionId: Long): List<Answer>
    suspend fun getSubjects(): List<Subject>
    suspend fun getSystems(subjectIds: List<Long>? = null): List<System>
    suspend fun getQuestionPerformance(qid: Long): com.medicalquiz.app.data.database.QuestionPerformance?
}