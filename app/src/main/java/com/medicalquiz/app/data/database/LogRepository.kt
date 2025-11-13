package com.medicalquiz.app.data.database

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for logging user answers with buffering
 */
class LogRepository(private val connection: DatabaseConnection) {
    
    private val logBuffer = mutableListOf<PendingLogEntry>()
    private val autoFlushThreshold = 10 // Flush after N answers
    
    data class PendingLogEntry(
        val qid: Long,
        val selectedAnswer: Int,
        val corrAnswer: Int,
        val time: Long,
        val answerDate: String,
        val testId: String
    )
    
    /**
     * Ensure logs table exists
     */
    fun ensureLogsTable() {
        val db = connection.getDatabase()
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                qid INTEGER NOT NULL,
                selectedAnswer INTEGER NOT NULL,
                corrAnswer INTEGER NOT NULL,
                time INTEGER NOT NULL,
                answerDate TEXT NOT NULL,
                testId TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
    
    /**
     * Log an answer (buffered)
     */
    suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        testId: String
    ) = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val answerDate = dateFormat.format(Date())
        
        val entry = PendingLogEntry(
            qid = qid,
            selectedAnswer = selectedAnswer,
            corrAnswer = corrAnswer,
            time = time,
            answerDate = answerDate,
            testId = testId
        )
        
        synchronized(logBuffer) {
            logBuffer.add(entry)
        }
        
        // Auto-flush if threshold reached
        if (logBuffer.size >= autoFlushThreshold) {
            flushLogs()
        }
    }
    
    /**
     * Flush all pending logs to database
     */
    suspend fun flushLogs(): Int = withContext(Dispatchers.IO) {
        val entriesToFlush = synchronized(logBuffer) {
            if (logBuffer.isEmpty()) return@withContext 0
            logBuffer.toList().also { logBuffer.clear() }
        }
        
        val db = connection.getDatabase()
        db.beginTransaction()
        try {
            entriesToFlush.forEach { entry ->
                insertLogEntry(entry)
            }
            db.setTransactionSuccessful()
            entriesToFlush.size
        } finally {
            db.endTransaction()
        }
    }
    
    private fun insertLogEntry(entry: PendingLogEntry) {
        val db = connection.getDatabase()
        val values = ContentValues().apply {
            put("qid", entry.qid)
            put("selectedAnswer", entry.selectedAnswer)
            put("corrAnswer", entry.corrAnswer)
            put("time", entry.time)
            put("answerDate", entry.answerDate)
            put("testId", entry.testId)
        }
        db.insert("logs", null, values)
    }
    
    /**
     * Get pending log count
     */
    fun getPendingLogCount(): Int = synchronized(logBuffer) { logBuffer.size }
    
    /**
     * Clear buffer without flushing
     */
    fun clearBuffer() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
    }
}
