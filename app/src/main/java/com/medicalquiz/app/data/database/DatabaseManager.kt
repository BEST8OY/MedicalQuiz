package com.medicalquiz.app.data.database

import com.medicalquiz.app.data.models.*

/**
 * Main database manager - coordinates repository operations
 * This is a facade that delegates to specialized repositories
 */
class DatabaseManager(dbPath: String) : DatabaseProvider {
    private val connection = DatabaseConnection(dbPath)
    private val questionRepository = QuestionRepository(connection)
    private val metadataRepository = MetadataRepository(connection)
    private val logRepository = LogRepository(connection)
    
    // ========================================================================
    // Connection Management
    // ========================================================================
    
    /**
     * Open the database connection
     */
    suspend fun openDatabase() {
        connection.open()
        logRepository.ensureLogsTable()
    }
    
    /**
     * Close the database connection
     */
    suspend fun closeDatabase() {
        logRepository.flushLogs()
        connection.close()
        logRepository.clearBuffer()
    }
    
    // ========================================================================
    // Question Queries - Delegated to QuestionRepository
    // ========================================================================
    
    suspend fun getQuestionIds(
        subjectIds: List<Long>? = null,
        systemIds: List<Long>? = null,
        performanceFilter: PerformanceFilter = PerformanceFilter.ALL
    ): List<Long> = questionRepository.getQuestionIds(subjectIds, systemIds, performanceFilter)
    
    suspend fun getQuestionById(id: Long): Question? = 
        questionRepository.getQuestionById(id)
    
    suspend fun getAnswersForQuestion(questionId: Long): List<Answer> = 
        questionRepository.getAnswersForQuestion(questionId)
    
    // ========================================================================
    // Metadata Queries - Delegated to MetadataRepository
    // ========================================================================
    
    suspend fun getSubjects(): List<Subject> = 
        metadataRepository.getSubjects()
    
    suspend fun getSystems(subjectIds: List<Long>? = null): List<System> = 
        metadataRepository.getSystems(subjectIds)
    
    // ========================================================================
    // Logging - Delegated to LogRepository
    // ========================================================================
    
    suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        testId: String
    ) = logRepository.logAnswer(qid, selectedAnswer, corrAnswer, time, testId)
    
    suspend fun flushLogs(): Int = logRepository.flushLogs()
    
    fun getPendingLogCount(): Int = logRepository.getPendingLogCount()

    suspend fun clearLogs() = logRepository.clearLogsTable()

    fun clearPendingLogsBuffer() = logRepository.clearBuffer()

    suspend fun getQuestionPerformance(qid: Long) = logRepository.getSummaryForQuestion(qid)

}
