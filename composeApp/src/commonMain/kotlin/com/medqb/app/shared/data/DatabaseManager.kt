package com.medqb.app.shared.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.database.QuizSessionHistoryRow
import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.Question
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.System
import com.medqb.app.shared.platform.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DatabaseManager(private val dbPath: String) : DatabaseProvider {
    private val driver = BundledSQLiteDriver()
    private var connection: SQLiteConnection? = null
    private val mutex = Mutex()
    private var isStringIds: Boolean = true

    suspend fun init() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                connection = driver.open(dbPath)
                getConnection().prepare("PRAGMA foreign_keys = ON").use { stmt ->
                    stmt.step()
                }
                checkSchema()
                ensureSessionLoggingSchema()
            } catch (e: Exception) {
                Logger.e("DatabaseManager", "Error initializing database", e)
                throw e
            }
        }
    }

    private fun checkSchema() {
        val conn = connection ?: throw IllegalStateException("Database not initialized")
        try {
            conn.prepare("SELECT type FROM pragma_table_info('Questions') WHERE name = 'subId'").use { stmt ->
                if (stmt.step()) {
                    val type = stmt.getText(0)
                    isStringIds = type.contains("char", ignoreCase = true) || 
                                  type.contains("text", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            Logger.e("DatabaseManager", "Schema check failed, defaulting to string IDs", e)
            isStringIds = true
        }
    }

    private fun getConnection(): SQLiteConnection {
        return connection ?: throw IllegalStateException("Database not initialized")
    }

    override suspend fun closeDatabase() = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection?.close()
            connection = null
        }
    }

    override suspend fun getQuestionIds(
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val args = mutableListOf<Any>()
            val whereClauses = mutableListOf<String>()

            subjectIds?.takeIf { it.isNotEmpty() }?.let {
                whereClauses.add(buildMultiValueCondition("q.subId", it, args))
            }

            systemIds?.takeIf { it.isNotEmpty() }?.let {
                whereClauses.add(buildMultiValueCondition("q.sysId", it, args))
            }

            buildPerformanceClause(performanceFilter)?.let { whereClauses.add(it) }

            val sql = buildString {
                append("SELECT q.id FROM Questions q")
                
                // Join with logs summary if needed for performance filtering
                if (performanceFilter != PerformanceFilter.ALL) {
                    append(" LEFT JOIN (")
                    append("   SELECT l.qid,")
                    append("     (CASE WHEN l.selectedAnswer = l.corrAnswer THEN 1 ELSE 0 END) as lastCorrect,")
                    append("     agg.everCorrect,")
                    append("     agg.everIncorrect")
                    append("   FROM logs l")
                    append("   JOIN (")
                    append("     SELECT qid,")
                    append("       MAX(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as everCorrect,")
                    append("       MAX(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as everIncorrect,")
                    append("       MAX(rowid) as lastRowId")
                    append("     FROM logs")
                    append("     GROUP BY qid")
                    append("   ) agg ON agg.qid = l.qid AND agg.lastRowId = l.rowid")
                    append(" ) ls ON ls.qid = q.id")
                }

                if (whereClauses.isNotEmpty()) {
                    append(" WHERE ")
                    append(whereClauses.joinToString(" AND "))
                }
                append(" ORDER BY q.id")
            }

            val result = mutableListOf<Long>()
            getConnection().prepare(sql).use { stmt ->
                bindArgs(stmt, args)
                while (stmt.step()) {
                    result.add(stmt.getLong(0))
                }
            }
            result
        }
    }

    override suspend fun getQuestionById(id: Long): Question? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                SELECT id, question, explanation, corrAns, title, mediaName, otherMedias, 
                       pplTaken, corrTaken, subId, sysId 
                FROM Questions WHERE id = ?
            """
            
            var question: Question? = null
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, id)
                if (stmt.step()) {
                    val subIdStr = if (stmt.isNull(9)) null else stmt.getText(9)
                    val sysIdStr = if (stmt.isNull(10)) null else stmt.getText(10)
                    
                    val subName = subIdStr?.let { getSubjectNames(it) }
                    val sysName = sysIdStr?.let { getSystemNames(it) }

                    question = Question(
                        id = stmt.getLong(0),
                        question = if (stmt.isNull(1)) "" else stmt.getText(1),
                        explanation = if (stmt.isNull(2)) "" else stmt.getText(2),
                        corrAns = if (stmt.isNull(3)) -1 else stmt.getLong(3).toInt(),
                        title = if (stmt.isNull(4)) null else stmt.getText(4),
                        mediaName = if (stmt.isNull(5)) null else stmt.getText(5),
                        otherMedias = if (stmt.isNull(6)) null else stmt.getText(6),
                        pplTaken = if (stmt.isNull(7)) null else stmt.getDouble(7),
                        corrTaken = if (stmt.isNull(8)) null else stmt.getDouble(8),
                        subId = subIdStr,
                        sysId = sysIdStr,
                        subName = subName,
                        sysName = sysName
                    )
                }
            }
            question
        }
    }

    private fun getSubjectNames(idsStr: String): String {
        val ids = idsStr.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return ""
        
        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT name FROM Subjects WHERE id IN ($placeholders)"
        
        val names = mutableListOf<String>()
        getConnection().prepare(sql).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
            while (stmt.step()) {
                if (!stmt.isNull(0)) {
                    names.add(stmt.getText(0))
                }
            }
        }
        return names.joinToString(", ")
    }

    private fun getSystemNames(idsStr: String): String {
        val ids = idsStr.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return ""
        
        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT name FROM Systems WHERE id IN ($placeholders)"
        
        val names = mutableListOf<String>()
        getConnection().prepare(sql).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
            while (stmt.step()) {
                if (!stmt.isNull(0)) {
                    names.add(stmt.getText(0))
                }
            }
        }
        return names.joinToString(", ")
    }

    override suspend fun getAnswersForQuestion(questionId: Long): List<Answer> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = "SELECT id, answerId, answerText, correctPercentage, qId FROM Answers WHERE qId = ?"
            val answers = mutableListOf<Answer>()
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, questionId)
                while (stmt.step()) {
                    answers.add(Answer(
                        answerId = if (stmt.isNull(1)) stmt.getLong(0) else stmt.getLong(1),
                        answerText = if (stmt.isNull(2)) "" else stmt.getText(2),
                        correctPercentage = if (stmt.isNull(3)) null else stmt.getLong(3).toInt(),
                        qId = if (stmt.isNull(4)) -1L else stmt.getLong(4)
                    ))
                }
            }
            answers
        }
    }

    override suspend fun getSubjects(): List<Subject> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = "SELECT id, name, count FROM Subjects ORDER BY name"
            val subjects = mutableListOf<Subject>()
            getConnection().prepare(sql).use { stmt ->
                while (stmt.step()) {
                    subjects.add(Subject(
                        id = stmt.getLong(0),
                        name = if (stmt.isNull(1)) "" else stmt.getText(1),
                        count = if (stmt.isNull(2)) 0 else stmt.getLong(2).toInt()
                    ))
                }
            }
            subjects
        }
    }

    override suspend fun getSystems(subjectIds: List<Long>?): List<System> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val systems = mutableListOf<System>()
            
            if (subjectIds.isNullOrEmpty()) {
                val sql = "SELECT id, name, count FROM Systems ORDER BY name"
                getConnection().prepare(sql).use { stmt ->
                    while (stmt.step()) {
                        systems.add(System(
                            id = stmt.getLong(0),
                            name = if (stmt.isNull(1)) "" else stmt.getText(1),
                            count = if (stmt.isNull(2)) 0 else stmt.getLong(2).toInt()
                        ))
                    }
                }
            } else {
                // Get system IDs from SubjectsSystems
                val placeholders = subjectIds.joinToString(",") { "?" }
                val sysIdSql = "SELECT DISTINCT sysId FROM SubjectsSystems WHERE subId IN ($placeholders)"
                val sysIds = mutableListOf<Long>()
                
                getConnection().prepare(sysIdSql).use { stmt ->
                    subjectIds.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
                    while (stmt.step()) {
                        sysIds.add(stmt.getLong(0))
                    }
                }
                
                if (sysIds.isNotEmpty()) {
                    val sysPlaceholders = sysIds.joinToString(",") { "?" }
                    val sql = "SELECT id, name, count FROM Systems WHERE id IN ($sysPlaceholders) ORDER BY name"
                    getConnection().prepare(sql).use { stmt ->
                        sysIds.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
                        while (stmt.step()) {
                            systems.add(System(
                                id = stmt.getLong(0),
                                name = if (stmt.isNull(1)) "" else stmt.getText(1),
                                count = if (stmt.isNull(2)) 0 else stmt.getLong(2).toInt()
                            ))
                        }
                    }
                }
            }
            systems
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        sessionId: String
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Clock.System.now()
            val dateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
            // Use month.ordinal + 1 for month number (1-12) and day property for day of month
            val monthNum = dateTime.month.ordinal + 1
            val dateString = "${dateTime.year}-${monthNum.toString().padStart(2, '0')}-${dateTime.day.toString().padStart(2, '0')} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}:${dateTime.second.toString().padStart(2, '0')}"
            
            val sql = "INSERT INTO logs (qid, selectedAnswer, corrAnswer, time, answerDate) VALUES (?, ?, ?, ?, ?)"
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, qid)
                stmt.bindLong(2, selectedAnswer.toLong())
                stmt.bindLong(3, corrAnswer.toLong())
                stmt.bindLong(4, time)
                stmt.bindText(5, dateString)
                stmt.step()
            }
            val insertedLogRowId = getLastInsertRowId()

            if (sessionId.isNotBlank()) {
                ensureSessionExistsLocked(sessionId)
                getConnection().prepare(
                    "INSERT OR IGNORE INTO session_log_links (session_id, log_rowid) VALUES (?, ?)"
                ).use { stmt ->
                    stmt.bindText(1, sessionId)
                    stmt.bindLong(2, insertedLogRowId)
                    stmt.step()
                }
            }
            Unit
        }
    }

    override suspend fun clearLogForQuestion(qid: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            getConnection().prepare("DELETE FROM logs WHERE qid = ?").use { stmt ->
                stmt.bindLong(1, qid)
                stmt.step()
            }
            Unit
        }
    }

    override suspend fun getQuestionPerformance(qid: Long): QuestionPerformance? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                SELECT
                   latest.lastCorrect,
                   agg.everCorrect,
                   agg.everIncorrect,
                   agg.attempts,
                   agg.correctCount,
                   agg.incorrectCount
                FROM (
                    SELECT
                        qid,
                        (CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as lastCorrect
                    FROM logs
                    WHERE qid = ?
                    ORDER BY rowid DESC
                    LIMIT 1
                ) latest
                JOIN (
                    SELECT
                        qid,
                        MAX(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as everCorrect,
                        MAX(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as everIncorrect,
                        COUNT(*) as attempts,
                        SUM(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as correctCount,
                        SUM(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as incorrectCount
                    FROM logs
                    WHERE qid = ?
                    GROUP BY qid
                ) agg ON agg.qid = latest.qid
            """
            
            var performance: QuestionPerformance? = null
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, qid)
                stmt.bindLong(2, qid)
                if (stmt.step()) {
                    performance = QuestionPerformance(
                        qid = qid,
                        lastCorrect = stmt.getLong(0) == 1L,
                        everCorrect = stmt.getLong(1) == 1L,
                        everIncorrect = stmt.getLong(2) == 1L,
                        attempts = stmt.getLong(3).toInt(),
                        correctCount = stmt.getLong(4).toInt(),
                        incorrectCount = stmt.getLong(5).toInt()
                    )
                }
            }
            performance
        }
    }

    private fun buildMultiValueCondition(
        columnAlias: String,
        ids: List<Long>,
        args: MutableList<Any>
    ): String {
        val normalizedIds = ids.distinct()
        if (normalizedIds.isEmpty()) return "1=1"

        if (!isStringIds) {
            // Integer IDs: use IN clause
            val placeholders = normalizedIds.joinToString(",") { "?" }
            args.addAll(normalizedIds)
            return "$columnAlias IN ($placeholders)"
        } else {
            // String IDs (comma separated): use instr
            return when (normalizedIds.size) {
                1 -> {
                    args.add(normalizedIds[0].toString())
                    "instr(',' || $columnAlias || ',', ',' || ? || ',') > 0"
                }
                else -> {
                    val conditions = normalizedIds.map { id ->
                        args.add(id.toString())
                        "instr(',' || $columnAlias || ',', ',' || ? || ',') > 0"
                    }
                    "(${conditions.joinToString(" OR ")})"
                }
            }
        }
    }

    private fun buildPerformanceClause(filter: PerformanceFilter): String? = when (filter) {
        PerformanceFilter.ALL -> null
        PerformanceFilter.UNANSWERED -> "ls.qid IS NULL"
        PerformanceFilter.LAST_CORRECT -> "ls.lastCorrect = 1"
        PerformanceFilter.LAST_INCORRECT -> "ls.lastCorrect = 0"
        PerformanceFilter.EVER_CORRECT -> "ls.everCorrect = 1"
        PerformanceFilter.EVER_INCORRECT -> "ls.everIncorrect = 1"
    }

    private fun bindArgs(stmt: SQLiteStatement, args: List<Any>) {
        args.forEachIndexed { index, arg ->
            val bindIndex = index + 1
            when (arg) {
                is String -> stmt.bindText(bindIndex, arg)
                is Long -> stmt.bindLong(bindIndex, arg)
                is Int -> stmt.bindLong(bindIndex, arg.toLong())
                is Double -> stmt.bindDouble(bindIndex, arg)
                is Float -> stmt.bindDouble(bindIndex, arg.toDouble())
                is Boolean -> stmt.bindLong(bindIndex, if (arg) 1L else 0L)
                else -> stmt.bindText(bindIndex, arg.toString())
            }
        }
    }

    private fun ensureSessionLoggingSchema() {
        val conn = getConnection()
        conn.prepare(
            """
            CREATE TABLE IF NOT EXISTS quiz_sessions (
                session_id TEXT PRIMARY KEY,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        ).use { stmt -> stmt.step() }

        conn.prepare(
            """
            CREATE TABLE IF NOT EXISTS session_log_links (
                session_id TEXT NOT NULL,
                log_rowid INTEGER NOT NULL,
                PRIMARY KEY (session_id, log_rowid),
                FOREIGN KEY (session_id) REFERENCES quiz_sessions(session_id) ON DELETE CASCADE
            )
            """.trimIndent()
        ).use { stmt -> stmt.step() }

        conn.prepare(
            """
            CREATE INDEX IF NOT EXISTS idx_session_log_links_session_id
            ON session_log_links(session_id)
            """.trimIndent()
        ).use { stmt -> stmt.step() }

        conn.prepare(
            """
            CREATE INDEX IF NOT EXISTS idx_session_log_links_log_rowid
            ON session_log_links(log_rowid)
            """.trimIndent()
        ).use { stmt -> stmt.step() }

        conn.prepare(
            """
            CREATE TRIGGER IF NOT EXISTS trg_logs_after_delete_cleanup_links
            AFTER DELETE ON logs
            BEGIN
                DELETE FROM session_log_links
                WHERE log_rowid = OLD.rowid;
            END
            """.trimIndent()
        ).use { stmt -> stmt.step() }

        conn.prepare(
            """
            CREATE TRIGGER IF NOT EXISTS trg_session_log_links_after_delete_logs
            AFTER DELETE ON session_log_links
            BEGIN
                DELETE FROM logs
                WHERE rowid = OLD.log_rowid
                  AND NOT EXISTS (
                      SELECT 1 FROM session_log_links
                      WHERE log_rowid = OLD.log_rowid
                  );
            END
            """.trimIndent()
        ).use { stmt -> stmt.step() }

        conn.prepare(
            """
            CREATE TABLE IF NOT EXISTS quiz_history (
                session_id TEXT PRIMARY KEY,
                database_name TEXT NOT NULL,
                entry_name TEXT NOT NULL DEFAULT '',
                selected_subject_ids TEXT NOT NULL DEFAULT '[]',
                selected_system_ids TEXT NOT NULL DEFAULT '[]',
                performance_filter TEXT NOT NULL DEFAULT 'ALL',
                current_question_index INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0,
                is_logging_enabled INTEGER NOT NULL DEFAULT 0,
                submission_mode TEXT NOT NULL DEFAULT 'INSTANT'
            )
            """.trimIndent()
        ).use { stmt -> stmt.step() }
    }

    private fun ensureSessionExistsLocked(sessionId: String) {
        getConnection().prepare(
            "INSERT OR IGNORE INTO quiz_sessions (session_id) VALUES (?)"
        ).use { stmt ->
            stmt.bindText(1, sessionId)
            stmt.step()
        }
    }

    private fun getLastInsertRowId(): Long {
        var rowId = -1L
        getConnection().prepare("SELECT last_insert_rowid()").use { stmt ->
            if (stmt.step()) {
                rowId = stmt.getLong(0)
            }
        }
        return rowId
    }

    override suspend fun upsertHistoryEntry(
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
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                INSERT OR REPLACE INTO quiz_history
                (session_id, database_name, entry_name, selected_subject_ids, selected_system_ids,
                 performance_filter, current_question_index, updated_at, is_logging_enabled, submission_mode)
                VALUES (?, ?, COALESCE(NULLIF(?, ''), entry_name), ?, ?, ?, ?, ?, ?, ?)
            """
            getConnection().prepare(sql).use { stmt ->
                stmt.bindText(1, sessionId)
                stmt.bindText(2, databaseName)
                stmt.bindText(3, entryName)
                stmt.bindText(4, selectedSubjectIds.joinToString(","))
                stmt.bindText(5, selectedSystemIds.joinToString(","))
                stmt.bindText(6, performanceFilter)
                stmt.bindLong(7, currentQuestionIndex.toLong())
                stmt.bindLong(8, updatedAt)
                stmt.bindLong(9, if (isLoggingEnabled) 1L else 0L)
                stmt.bindText(10, submissionMode)
                stmt.step()
            }
            Unit
        }
    }

    override suspend fun listHistoryEntries(): List<QuizSessionHistoryRow> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                SELECT session_id, database_name, entry_name, selected_subject_ids, selected_system_ids,
                       performance_filter, current_question_index, updated_at, is_logging_enabled, submission_mode
                FROM quiz_history
                ORDER BY updated_at DESC
            """
            val result = mutableListOf<QuizSessionHistoryRow>()
            getConnection().prepare(sql).use { stmt ->
                while (stmt.step()) {
                    result.add(
                        QuizSessionHistoryRow(
                            sessionId = stmt.getText(0),
                            databaseName = stmt.getText(1),
                            entryName = stmt.getText(2),
                            selectedSubjectIds = stmt.getText(3).split(",").mapNotNull { it.trim().toLongOrNull() },
                            selectedSystemIds = stmt.getText(4).split(",").mapNotNull { it.trim().toLongOrNull() },
                            performanceFilter = stmt.getText(5),
                            currentQuestionIndex = stmt.getLong(6).toInt(),
                            updatedAt = stmt.getLong(7),
                            isLoggingEnabled = stmt.getLong(8) == 1L,
                            submissionMode = stmt.getText(9),
                        )
                    )
                }
            }
            result
        }
    }

    override suspend fun getHistoryEntry(sessionId: String): QuizSessionHistoryRow? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                SELECT session_id, database_name, entry_name, selected_subject_ids, selected_system_ids,
                       performance_filter, current_question_index, updated_at, is_logging_enabled, submission_mode
                FROM quiz_history WHERE session_id = ?
            """
            getConnection().prepare(sql).use { stmt ->
                stmt.bindText(1, sessionId)
                if (stmt.step()) {
                    QuizSessionHistoryRow(
                        sessionId = stmt.getText(0),
                        databaseName = stmt.getText(1),
                        entryName = stmt.getText(2),
                        selectedSubjectIds = stmt.getText(3).split(",").mapNotNull { it.trim().toLongOrNull() },
                        selectedSystemIds = stmt.getText(4).split(",").mapNotNull { it.trim().toLongOrNull() },
                        performanceFilter = stmt.getText(5),
                        currentQuestionIndex = stmt.getLong(6).toInt(),
                        updatedAt = stmt.getLong(7),
                        isLoggingEnabled = stmt.getLong(8) == 1L,
                        submissionMode = stmt.getText(9),
                    )
                } else null
            }
        }
    }

    override suspend fun deleteHistoryEntries(sessionIds: List<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (sessionIds.isEmpty()) return@withContext
            val placeholders = sessionIds.joinToString(",") { "?" }
            getConnection().prepare("DELETE FROM quiz_history WHERE session_id IN ($placeholders)").use { stmt ->
                sessionIds.forEachIndexed { index, id -> stmt.bindText(index + 1, id) }
                stmt.step()
            }
            Unit
        }
    }

    override suspend fun renameHistoryEntry(sessionId: String, newName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            getConnection().prepare("UPDATE quiz_history SET entry_name = ? WHERE session_id = ?").use { stmt ->
                stmt.bindText(1, newName)
                stmt.bindText(2, sessionId)
                stmt.step()
            }
            Unit
        }
    }
}
