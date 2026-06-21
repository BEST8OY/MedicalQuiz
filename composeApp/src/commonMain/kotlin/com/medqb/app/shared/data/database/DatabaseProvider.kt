package com.medqb.app.shared.data.database

import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.Question
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.System

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
        sessionId: String
    )
    
    suspend fun clearLogForQuestion(qid: Long)
    suspend fun getQuestionPerformance(qid: Long): QuestionPerformance?

    suspend fun upsertHistoryEntry(
        sessionId: String,
        databaseName: String,
        entryName: String,
        selectedSubjectIds: List<Long>,
        selectedSystemIds: List<Long>,
        performanceFilter: String,
        currentQuestionIndex: Int,
        updatedAt: Long,
        isLoggingEnabled: Boolean,
        submissionMode: String,
    )

    suspend fun listHistoryEntries(): List<QuizSessionHistoryRow>
    suspend fun getHistoryEntry(sessionId: String): QuizSessionHistoryRow?
    suspend fun deleteHistoryEntries(sessionIds: List<String>)
    suspend fun renameHistoryEntry(sessionId: String, newName: String)
}
