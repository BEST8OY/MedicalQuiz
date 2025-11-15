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
    
    // Prepared statements for better performance
    private val insertLogStmt by lazy {
        connection.getDatabase().compileStatement(
            "INSERT INTO logs (qid, selectedAnswer, corrAnswer, time, answerDate, testId) VALUES (?, ?, ?, ?, ?, ?)"
        )
    }
    
    private val selectSummaryStmt by lazy {
        connection.getDatabase().compileStatement(
            "SELECT lastCorrect, everCorrect, everIncorrect, attempts FROM logs_summary WHERE qid = ?"
        )
    }
    
    private val upsertSummaryStmt by lazy {
        connection.getDatabase().compileStatement(
            "INSERT OR REPLACE INTO logs_summary (qid, lastCorrect, everCorrect, everIncorrect, attempts) VALUES (?, ?, ?, ?, ?)"
        )
    }
    
    private val selectPerformanceStmt by lazy {
        connection.getDatabase().compileStatement(
            "SELECT lastCorrect, everCorrect, everIncorrect, attempts FROM logs_summary WHERE qid = ?"
        )
    }
    
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
    suspend fun ensureLogsTable() = withContext(Dispatchers.IO) {
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
            // Group entries by question ID for batch summary updates
            val entriesByQuestion = entriesToFlush.groupBy { it.qid }
            
            entriesToFlush.forEach { entry ->
                insertLogEntry(entry)
            }
            
            // Batch update summaries by question
            entriesByQuestion.forEach { (qid, questionEntries) ->
                updateSummaryBatch(qid, questionEntries)
            }
            
            db.setTransactionSuccessful()
            entriesToFlush.size
        } finally {
            db.endTransaction()
        }
    }
    
    private fun insertLogEntry(entry: PendingLogEntry) {
        insertLogStmt.bindLong(1, entry.qid)
        insertLogStmt.bindLong(2, entry.selectedAnswer.toLong())
        insertLogStmt.bindLong(3, entry.corrAnswer.toLong())
        insertLogStmt.bindLong(4, entry.time)
        insertLogStmt.bindString(5, entry.answerDate)
        insertLogStmt.bindString(6, entry.testId)
        insertLogStmt.executeInsert()
        insertLogStmt.clearBindings()
    }

    private fun updateSummaryBatch(qid: Long, entries: List<PendingLogEntry>) {
        // Get current summary state
        val currentState = getCurrentSummary(qid)
        
        // Calculate new summary state from all entries
        var everCorrect = currentState.everCorrect
        var everIncorrect = currentState.everIncorrect
        var attempts = currentState.attempts
        var lastCorrect = currentState.lastCorrect
        
        entries.forEach { entry ->
            val wasCorrect = entry.selectedAnswer == entry.corrAnswer
            everCorrect = everCorrect or if (wasCorrect) 1 else 0
            everIncorrect = everIncorrect or if (!wasCorrect) 1 else 0
            attempts += 1
            lastCorrect = if (wasCorrect) 1 else 0
        }
        
        // Update summary with final state
        upsertSummaryStmt.bindLong(1, qid)
        upsertSummaryStmt.bindLong(2, lastCorrect.toLong())
        upsertSummaryStmt.bindLong(3, everCorrect.toLong())
        upsertSummaryStmt.bindLong(4, everIncorrect.toLong())
        upsertSummaryStmt.bindLong(5, attempts.toLong())
        upsertSummaryStmt.executeInsert()
        upsertSummaryStmt.clearBindings()
    }
    
    private data class SummaryState(val lastCorrect: Int, val everCorrect: Int, val everIncorrect: Int, val attempts: Int)
    
    private fun getCurrentSummary(qid: Long): SummaryState {
        val db = connection.getDatabase()
        val existsCursor = db.rawQuery("SELECT 1 FROM logs_summary WHERE qid = ?", arrayOf(qid.toString()))
        val exists = existsCursor.use { it.moveToFirst() }
        if (exists) {
            val fullCursor = db.rawQuery(
                "SELECT lastCorrect, everCorrect, everIncorrect, attempts FROM logs_summary WHERE qid = ?",
                arrayOf(qid.toString())
            )
            fullCursor.use {
                if (it.moveToFirst()) {
                    SummaryState(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3))
                } else {
                    SummaryState(0, 0, 0, 0)
                }
            }
        } else {
            SummaryState(0, 0, 0, 0)
        }
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
        val existsCursor = db.rawQuery("SELECT 1 FROM logs_summary WHERE qid = ?", arrayOf(qid.toString()))
        val exists = existsCursor.use { it.moveToFirst() }
        if (exists) {
            val fullCursor = db.rawQuery(
                "SELECT lastCorrect, everCorrect, everIncorrect, attempts FROM logs_summary WHERE qid = ?",
                arrayOf(qid.toString())
            )
            fullCursor.use {
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
        } else {
            null
        }
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
