package com.medicalquiz.app.shared.data.database

interface LogsProvider {
    suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        sessionId: String
    )

    suspend fun clearLogForQuestion(qid: Long)
    suspend fun getQuestionPerformance(qid: Long): QuestionPerformance?
    suspend fun getQuestionIdsByPerformance(qids: List<Long>, filter: PerformanceFilter): List<Long>
    suspend fun close()
}
