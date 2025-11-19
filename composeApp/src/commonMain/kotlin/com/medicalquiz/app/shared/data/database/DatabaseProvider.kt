package com.medicalquiz.app.shared.data.database

import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.data.models.Question
import com.medicalquiz.app.shared.data.models.Subject
import com.medicalquiz.app.shared.data.models.System

interface DatabaseProvider {
    suspend fun closeDatabase()
    
    suspend fun getQuestionIds(
        subjectIds: List<Long>? = null,
        systemIds: List<Long>? = null,
        performanceFilter: PerformanceFilter = PerformanceFilter.ALL
    ): List<Long>
    
    suspend fun getQuestionById(id: Long): Question?
    suspend fun getAnswersForQuestion(questionId: Long): List<Answer>
    
    suspend fun getSubjects(): List<Subject>
    suspend fun getSystems(subjectIds: List<Long>? = null): List<System>
    
    suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        testId: String
    )
    
    suspend fun flushLogs(): Int
    suspend fun clearLogs()
    suspend fun clearPendingLogsBuffer()
    suspend fun getQuestionPerformance(qid: Long): QuestionPerformance?
}
