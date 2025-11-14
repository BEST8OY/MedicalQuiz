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
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS logs_summary (
                qid INTEGER PRIMARY KEY,
                lastCorrect INTEGER NOT NULL,
                everCorrect INTEGER NOT NULL,
                everIncorrect INTEGER NOT NULL,
                attempts INTEGER NOT NULL
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
                updateSummary(entry)
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

    private fun updateSummary(entry: PendingLogEntry) {
        val db = connection.getDatabase()
        val cursor = db.rawQuery(
            "SELECT lastCorrect, everCorrect, everIncorrect, attempts FROM logs_summary WHERE qid = ?",
            arrayOf(entry.qid.toString())
        )

        val (everCorrect, everIncorrect, attemptsPrev) = cursor.use {
            if (it.moveToFirst()) {
                Triple(it.getInt(1), it.getInt(2), it.getInt(3))
            } else {
                Triple(0, 0, 0)
            }
        }

        val wasCorrect = if (entry.selectedAnswer == entry.corrAnswer) 1 else 0
        val newEverCorrect = everCorrect or wasCorrect
        val newEverIncorrect = everIncorrect or (1 - wasCorrect)
        val newAttempts = attemptsPrev + 1

        val values = ContentValues().apply {
            put("qid", entry.qid)
            put("lastCorrect", wasCorrect)
            put("everCorrect", newEverCorrect)
            put("everIncorrect", newEverIncorrect)
            put("attempts", newAttempts)
        }

        db.insertWithOnConflict("logs_summary", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
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

    /**
     * Remove all persisted log entries
     */
    suspend fun clearLogsTable(): Unit = withContext(Dispatchers.IO) {
        val db = connection.getDatabase()
        db.execSQL("DELETE FROM logs")
        db.execSQL("DELETE FROM logs_summary")
        clearBuffer()
    }

    suspend fun getSummaryForQuestion(qid: Long): QuestionPerformance? = withContext(Dispatchers.IO) {
        val db = connection.getDatabase()
        val cursor = db.rawQuery(
            "SELECT lastCorrect, everCorrect, everIncorrect, attempts FROM logs_summary WHERE qid = ?",
            arrayOf(qid.toString())
        )
        cursor.use {
            if (it.moveToFirst()) {
                QuestionPerformance(
                    qid = qid,
                    lastCorrect = it.getInt(0) == 1,
                    everCorrect = it.getInt(1) == 1,
                    everIncorrect = it.getInt(2) == 1,
                    attempts = it.getInt(3)
                )
            } else {
                null
            }
        }
    }

    suspend fun getQuestionIdsByPerformance(filter: PerformanceFilter): Set<Long> = withContext(Dispatchers.IO) {
        val db = connection.getDatabase()
        val sql = when (filter) {
            PerformanceFilter.UNANSWERED -> "SELECT id FROM Questions WHERE id NOT IN (SELECT DISTINCT qid FROM logs_summary)"
            PerformanceFilter.LAST_CORRECT -> "SELECT qid FROM logs_summary WHERE lastCorrect = 1"
            PerformanceFilter.LAST_INCORRECT -> "SELECT qid FROM logs_summary WHERE lastCorrect = 0"
            PerformanceFilter.EVER_CORRECT -> "SELECT qid FROM logs_summary WHERE everCorrect = 1"
            PerformanceFilter.EVER_INCORRECT -> "SELECT qid FROM logs_summary WHERE everIncorrect = 1"
            PerformanceFilter.ALL -> "SELECT id FROM Questions"
        }
        val ids = mutableSetOf<Long>()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0))
            }
        }
        ids
    }
}

data class QuestionPerformance(
    val qid: Long,
    val lastCorrect: Boolean,
    val everCorrect: Boolean,
    val everIncorrect: Boolean,
    val attempts: Int
)

enum class PerformanceFilter(val storageValue: String) {
    ALL("all"),
    UNANSWERED("unanswered"),
    LAST_CORRECT("correct"),
    LAST_INCORRECT("incorrect"),
    EVER_CORRECT("ever-correct"),
    EVER_INCORRECT("ever-incorrect")
}
