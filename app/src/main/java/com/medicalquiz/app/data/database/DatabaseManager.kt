package com.medicalquiz.app.data.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.medicalquiz.app.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DatabaseManager(private val dbPath: String) {
    private var database: SQLiteDatabase? = null
    private val logBuffer = mutableListOf<PendingLogEntry>()
    
    // Buffering configuration
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
     * Open the database connection
     */
    suspend fun openDatabase() = withContext(Dispatchers.IO) {
        if (database?.isOpen == true) return@withContext
        
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            throw IllegalStateException("Database file not found: $dbPath")
        }
        
        database = SQLiteDatabase.openDatabase(
            dbPath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        
        // Ensure logs table exists
        ensureLogsTable()
    }
    
    /**
     * Close the database connection
     */
    suspend fun closeDatabase() {
        // Flush any pending logs before closing
        flushLogs()
        database?.close()
        database = null
        logBuffer.clear()
    }
    
    private fun getDb(): SQLiteDatabase {
        return database ?: throw IllegalStateException("Database not opened")
    }
    
    // ========================================================================
    // Question Queries
    // ========================================================================
    
    /**
     * Get all question IDs, optionally filtered by subjects and systems
     */
    suspend fun getQuestionIds(
        subjectIds: List<Long>? = null,
        systemIds: List<Long>? = null
    ): List<Long> = withContext(Dispatchers.IO) {
        val db = getDb()
        val questionIds = mutableListOf<Long>()
        
        var sql = "SELECT id FROM Questions ORDER BY id"
        val args = mutableListOf<String>()
        
        // Build WHERE conditions for comma-separated IDs
        if (!subjectIds.isNullOrEmpty() || !systemIds.isNullOrEmpty()) {
            val conditions = mutableListOf<String>()
            
            if (!subjectIds.isNullOrEmpty()) {
                val subConditions = subjectIds.joinToString(" OR ") { id ->
                    args.add(id.toString())
                    args.add("$id,%")
                    args.add("%,$id,%")
                    args.add("%,$id")
                    "(subId = ? OR subId LIKE ? OR subId LIKE ? OR subId LIKE ?)"
                }
                conditions.add("($subConditions)")
            }
            
            if (!systemIds.isNullOrEmpty()) {
                val sysConditions = systemIds.joinToString(" OR ") { id ->
                    args.add(id.toString())
                    args.add("$id,%")
                    args.add("%,$id,%")
                    args.add("%,$id")
                    "(sysId = ? OR sysId LIKE ? OR sysId LIKE ? OR sysId LIKE ?)"
                }
                conditions.add("($sysConditions)")
            }
            
            sql = "SELECT DISTINCT id FROM Questions WHERE ${conditions.joinToString(" AND ")} ORDER BY id"
        }
        
        val cursor = db.rawQuery(sql, args.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                questionIds.add(it.getLong(0))
            }
        }
        
        questionIds
    }
    
    /**
     * Get a question by its ID
     */
    suspend fun getQuestionById(id: Long): Question? = withContext(Dispatchers.IO) {
        val db = getDb()
        val sql = """
            SELECT 
                id, question, explanation, corrAns,
                title, mediaName, otherMedias,
                pplTaken, corrTaken, subId, sysId
            FROM Questions
            WHERE id = ?
        """
        
        val cursor = db.rawQuery(sql, arrayOf(id.toString()))
        cursor.use {
            if (it.moveToFirst()) {
                val question = Question(
                    id = it.getLong(0),
                    question = it.getString(1),
                    explanation = it.getString(2),
                    corrAns = it.getInt(3),
                    title = it.getStringOrNull(4),
                    mediaName = it.getStringOrNull(5),
                    otherMedias = it.getStringOrNull(6),
                    pplTaken = it.getDoubleOrNull(7),
                    corrTaken = it.getDoubleOrNull(8),
                    subId = it.getStringOrNull(9),
                    sysId = it.getStringOrNull(10)
                )
                
                // Resolve names
                question.subName = resolveNames("Subjects", question.subId)
                question.sysName = resolveNames("Systems", question.sysId)
                
                question
            } else {
                null
            }
        }
    }
    
    /**
     * Get all answers for a question
     */
    suspend fun getAnswersForQuestion(questionId: Long): List<Answer> = withContext(Dispatchers.IO) {
        val db = getDb()
        val sql = """
            SELECT answerId, answerText, correctPercentage
            FROM Answers
            WHERE qId = ?
            ORDER BY answerId
        """
        
        val answers = mutableListOf<Answer>()
        val cursor = db.rawQuery(sql, arrayOf(questionId.toString()))
        cursor.use {
            while (it.moveToNext()) {
                answers.add(
                    Answer(
                        answerId = it.getLong(0),
                        answerText = it.getString(1),
                        correctPercentage = it.getIntOrNull(2)
                    )
                )
            }
        }
        
        answers
    }
    
    // ========================================================================
    // Filter Queries
    // ========================================================================
    
    /**
     * Get all subjects
     */
    suspend fun getSubjects(): List<Subject> = withContext(Dispatchers.IO) {
        val db = getDb()
        val sql = "SELECT id, name, count FROM Subjects ORDER BY name"
        val subjects = mutableListOf<Subject>()
        
        val cursor = db.rawQuery(sql, null)
        cursor.use {
            while (it.moveToNext()) {
                subjects.add(
                    Subject(
                        id = it.getLong(0),
                        name = it.getString(1),
                        count = it.getInt(2)
                    )
                )
            }
        }
        
        subjects
    }
    
    /**
     * Get all systems, optionally filtered by subject IDs
     */
    suspend fun getSystems(subjectIds: List<Long>? = null): List<System> = withContext(Dispatchers.IO) {
        val db = getDb()
        val systems = mutableListOf<System>()
        
        val sql = if (subjectIds.isNullOrEmpty()) {
            val cursor = db.rawQuery("SELECT id, name, count FROM Systems ORDER BY name", null)
            cursor.use {
                while (it.moveToNext()) {
                    systems.add(
                        System(
                            id = it.getLong(0),
                            name = it.getString(1),
                            count = it.getInt(2)
                        )
                    )
                }
            }
        } else {
            val placeholders = subjectIds.joinToString(",") { "?" }
            val query = """
                SELECT s.id, s.name, ss.count
                FROM Systems s
                JOIN SubjectsSystems ss ON s.id = ss.sysId
                WHERE ss.subId IN ($placeholders) AND ss.count > 0
                GROUP BY s.id, s.name
                ORDER BY s.name
            """
            val cursor = db.rawQuery(query, subjectIds.map { it.toString() }.toTypedArray())
            cursor.use {
                while (it.moveToNext()) {
                    systems.add(
                        System(
                            id = it.getLong(0),
                            name = it.getString(1),
                            count = it.getInt(2)
                        )
                    )
                }
            }
        }
        
        systems
    }
    
    // ========================================================================
    // Logging Functions
    // ========================================================================
    
    /**
     * Ensure the logs table exists
     */
    private fun ensureLogsTable() {
        val db = getDb()
        val sql = """
            CREATE TABLE IF NOT EXISTS logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                qid INTEGER,
                selectedAnswer INTEGER,
                corrAnswer INTEGER,
                time INTEGER,
                answerDate TEXT,
                testId TEXT
            )
        """
        db.execSQL(sql)
        
        // Create index
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_qid ON logs(qid)")
    }
    
    /**
     * Log an answer (buffered for performance)
     * Use immediate=true for critical answers that must be persisted immediately
     */
    suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        testId: String,
        immediate: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val entry = PendingLogEntry(
            qid = qid,
            selectedAnswer = selectedAnswer,
            corrAnswer = corrAnswer,
            time = time,
            answerDate = getCurrentTimestamp(),
            testId = testId
        )
        
        if (immediate) {
            // Write immediately without buffering
            insertLogEntry(entry)
        } else {
            // Add to buffer
            logBuffer.add(entry)
            
            // Auto-flush if buffer reaches threshold
            if (logBuffer.size >= autoFlushThreshold) {
                flushLogs()
            }
        }
    }
    
    /**
     * Insert a single log entry to the database
     */
    private fun insertLogEntry(entry: PendingLogEntry) {
        val db = getDb()
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
     * Flush all buffered logs to the database
     * Returns the number of logs flushed
     */
    suspend fun flushLogs(): Int = withContext(Dispatchers.IO) {
        if (logBuffer.isEmpty()) return@withContext 0
        
        val db = getDb()
        val flushedCount = logBuffer.size
        
        // Use transaction for batch insert (much faster)
        db.beginTransaction()
        try {
            logBuffer.forEach { entry ->
                insertLogEntry(entry)
            }
            db.setTransactionSuccessful()
            logBuffer.clear()
        } finally {
            db.endTransaction()
        }
        
        flushedCount
    }
    
    /**
     * Get number of pending logs in buffer
     */
    fun getPendingLogCount(): Int = logBuffer.size
    
    // ========================================================================
    // Statistics Queries
    // ========================================================================
    
    /**
     * Get the status of a question (last attempt)
     * Checks buffer first, then database
     */
    suspend fun getQuestionStatus(qid: Long): QuestionStatus = withContext(Dispatchers.IO) {
        // Check buffer first for most recent status
        val bufferedEntry = logBuffer.lastOrNull { it.qid == qid }
        if (bufferedEntry != null) {
            return@withContext if (bufferedEntry.selectedAnswer == bufferedEntry.corrAnswer) {
                QuestionStatus.CORRECT
            } else {
                QuestionStatus.INCORRECT
            }
        }
        
        // Check database
        val db = getDb()
        val sql = """
            SELECT selectedAnswer, corrAnswer FROM logs
            WHERE qid = ?
            ORDER BY id DESC
            LIMIT 1
        """
        
        val cursor = db.rawQuery(sql, arrayOf(qid.toString()))
        cursor.use {
            if (it.moveToFirst()) {
                val selectedAnswer = it.getInt(0)
                val corrAnswer = it.getInt(1)
                if (selectedAnswer == corrAnswer) QuestionStatus.CORRECT else QuestionStatus.INCORRECT
            } else {
                QuestionStatus.UNANSWERED
            }
        }
    }
    
    /**
     * Check if question has ever been answered correctly
     */
    suspend fun hasEverBeenCorrect(qid: Long): Boolean = withContext(Dispatchers.IO) {
        val db = getDb()
        val sql = "SELECT 1 FROM logs WHERE qid = ? AND selectedAnswer = corrAnswer LIMIT 1"
        val cursor = db.rawQuery(sql, arrayOf(qid.toString()))
        cursor.use { it.count > 0 }
    }
    
    /**
     * Check if question has ever been answered incorrectly
     */
    suspend fun hasEverBeenIncorrect(qid: Long): Boolean = withContext(Dispatchers.IO) {
        val db = getDb()
        val sql = "SELECT 1 FROM logs WHERE qid = ? AND selectedAnswer != corrAnswer LIMIT 1"
        val cursor = db.rawQuery(sql, arrayOf(qid.toString()))
        cursor.use { it.count > 0 }
    }
    
    /**
     * Get statistics for a question
     * Includes both flushed logs and buffered logs
     */
    suspend fun getQuestionStats(qid: Long): QuestionStats = withContext(Dispatchers.IO) {
        // Get database stats
        val db = getDb()
        val sql = """
            SELECT 
                COUNT(*) as attempts,
                SUM(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as correct,
                SUM(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as incorrect
            FROM logs
            WHERE qid = ?
        """
        
        val cursor = db.rawQuery(sql, arrayOf(qid.toString()))
        val dbStats = cursor.use {
            if (it.moveToFirst()) {
                QuestionStats(
                    attempts = it.getInt(0),
                    correct = it.getInt(1),
                    incorrect = it.getInt(2)
                )
            } else {
                QuestionStats()
            }
        }
        
        // Add buffered stats
        val bufferedEntries = logBuffer.filter { it.qid == qid }
        val bufferedStats = QuestionStats(
            attempts = bufferedEntries.size,
            correct = bufferedEntries.count { it.selectedAnswer == it.corrAnswer },
            incorrect = bufferedEntries.count { it.selectedAnswer != it.corrAnswer }
        )
        
        // Combine
        QuestionStats(
            attempts = dbStats.attempts + bufferedStats.attempts,
            correct = dbStats.correct + bufferedStats.correct,
            incorrect = dbStats.incorrect + bufferedStats.incorrect
        )
    }
    
    /**
     * Get recent activity logs
     */
    suspend fun getRecentActivity(limit: Int = 10): List<LogEntry> = withContext(Dispatchers.IO) {
        val db = getDb()
        val sql = """
            SELECT id, qid, selectedAnswer, corrAnswer, time, answerDate, testId
            FROM logs
            ORDER BY id DESC
            LIMIT ?
        """
        
        val logs = mutableListOf<LogEntry>()
        val cursor = db.rawQuery(sql, arrayOf(limit.toString()))
        cursor.use {
            while (it.moveToNext()) {
                logs.add(
                    LogEntry(
                        id = it.getLong(0),
                        qid = it.getLong(1),
                        selectedAnswer = it.getInt(2),
                        corrAnswer = it.getInt(3),
                        time = it.getLong(4),
                        answerDate = it.getString(5),
                        testId = it.getString(6)
                    )
                )
            }
        }
        
        logs.reversed()
    }
    
    /**
     * Reset all logs
     */
    suspend fun resetLogs(): Int = withContext(Dispatchers.IO) {
        val db = getDb()
        val count = db.delete("logs", null, null)
        count
    }
    
    // ========================================================================
    // Helper Functions
    // ========================================================================
    
    /**
     * Resolve comma-separated IDs to names
     */
    private fun resolveNames(table: String, idValue: String?): String? {
        if (idValue.isNullOrBlank()) return null
        
        val ids = idValue.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
        
        if (ids.isEmpty()) return null
        
        val db = getDb()
        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT name FROM $table WHERE id IN ($placeholders) ORDER BY name"
        
        val names = mutableListOf<String>()
        val cursor = db.rawQuery(sql, ids.map { it.toString() }.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                names.add(it.getString(0))
            }
        }
        
        return if (names.isNotEmpty()) names.joinToString(", ") else null
    }
    
    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}

// Extension functions for Cursor
private fun Cursor.getStringOrNull(columnIndex: Int): String? {
    return if (isNull(columnIndex)) null else getString(columnIndex)
}

private fun Cursor.getDoubleOrNull(columnIndex: Int): Double? {
    return if (isNull(columnIndex)) null else getDouble(columnIndex)
}

private fun Cursor.getIntOrNull(columnIndex: Int): Int? {
    return if (isNull(columnIndex)) null else getInt(columnIndex)
}
