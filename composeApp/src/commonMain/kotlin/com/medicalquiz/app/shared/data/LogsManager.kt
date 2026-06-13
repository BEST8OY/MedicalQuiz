package com.medicalquiz.app.shared.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medicalquiz.app.shared.data.database.LogsProvider
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.database.QuestionPerformance
import com.medicalquiz.app.shared.platform.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class LogsManager(private val dbPath: String) : LogsProvider {
    private val driver = BundledSQLiteDriver()
    private var connection: SQLiteConnection? = null
    private val mutex = Mutex()

    suspend fun init() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val conn = driver.open(dbPath)
                createSchema(conn)
                connection = conn
            } catch (e: Exception) {
                Logger.e("LogsManager", "Error initializing logs database", e)
                throw e
            }
        }
    }

    private fun getConnection(): SQLiteConnection {
        return connection ?: throw IllegalStateException("LogsManager not initialized")
    }

    private fun createSchema(conn: SQLiteConnection) {
        conn.prepare(
            """
            CREATE TABLE IF NOT EXISTS logs (
                qid INTEGER NOT NULL,
                selectedAnswer INTEGER NOT NULL,
                corrAnswer INTEGER NOT NULL,
                time INTEGER NOT NULL,
                answerDate TEXT NOT NULL
            )
            """.trimIndent()
        ).use { it.step() }

        conn.prepare(
            """
            CREATE INDEX IF NOT EXISTS idx_logs_qid ON logs(qid)
            """.trimIndent()
        ).use { it.step() }

        conn.prepare(
            """
            CREATE TABLE IF NOT EXISTS quiz_sessions (
                session_id TEXT PRIMARY KEY,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        ).use { it.step() }

        conn.prepare(
            """
            CREATE TABLE IF NOT EXISTS session_log_links (
                session_id TEXT NOT NULL,
                log_rowid INTEGER NOT NULL,
                PRIMARY KEY (session_id, log_rowid),
                FOREIGN KEY (session_id) REFERENCES quiz_sessions(session_id) ON DELETE CASCADE
            )
            """.trimIndent()
        ).use { it.step() }

        conn.prepare(
            """
            CREATE INDEX IF NOT EXISTS idx_session_log_links_session_id
            ON session_log_links(session_id)
            """.trimIndent()
        ).use { it.step() }

        conn.prepare(
            """
            CREATE INDEX IF NOT EXISTS idx_session_log_links_log_rowid
            ON session_log_links(log_rowid)
            """.trimIndent()
        ).use { it.step() }

        conn.prepare(
            """
            CREATE TRIGGER IF NOT EXISTS trg_logs_after_delete_cleanup_links
            AFTER DELETE ON logs
            BEGIN
                DELETE FROM session_log_links
                WHERE log_rowid = OLD.rowid;
            END
            """.trimIndent()
        ).use { it.step() }

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
        ).use { it.step() }
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

    override suspend fun getQuestionIdsByPerformance(
        qids: List<Long>,
        filter: PerformanceFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        if (filter == PerformanceFilter.ALL || qids.isEmpty()) return@withContext qids

        mutex.withLock {
            val result = mutableListOf<Long>()

            when (filter) {
                PerformanceFilter.UNANSWERED -> {
                    val placeholders = qids.joinToString(",") { "?" }
                    val sql = "SELECT qid FROM logs WHERE qid IN ($placeholders) GROUP BY qid"
                    val answered = mutableSetOf<Long>()
                    getConnection().prepare(sql).use { stmt ->
                        qids.forEachIndexed { i, id -> stmt.bindLong(i + 1, id) }
                        while (stmt.step()) {
                            answered.add(stmt.getLong(0))
                        }
                    }
                    result.addAll(qids.filterNot { it in answered })
                }
                PerformanceFilter.LAST_CORRECT -> {
                    val sql = """
                        SELECT qid FROM (
                            SELECT qid, (CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as lastCorrect
                            FROM logs ORDER BY rowid DESC
                        ) GROUP BY qid HAVING lastCorrect = 1
                    """
                    val matching = mutableSetOf<Long>()
                    getConnection().prepare(sql).use { stmt ->
                        while (stmt.step()) {
                            matching.add(stmt.getLong(0))
                        }
                    }
                    result.addAll(qids.filter { it in matching })
                }
                PerformanceFilter.LAST_INCORRECT -> {
                    val sql = """
                        SELECT qid FROM (
                            SELECT qid, (CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as lastCorrect
                            FROM logs ORDER BY rowid DESC
                        ) GROUP BY qid HAVING lastCorrect = 0
                    """
                    val matching = mutableSetOf<Long>()
                    getConnection().prepare(sql).use { stmt ->
                        while (stmt.step()) {
                            matching.add(stmt.getLong(0))
                        }
                    }
                    result.addAll(qids.filter { it in matching })
                }
                PerformanceFilter.EVER_CORRECT -> {
                    val sql = """
                        SELECT qid FROM logs
                        WHERE selectedAnswer = corrAnswer
                        GROUP BY qid
                    """
                    val matching = mutableSetOf<Long>()
                    getConnection().prepare(sql).use { stmt ->
                        while (stmt.step()) {
                            matching.add(stmt.getLong(0))
                        }
                    }
                    result.addAll(qids.filter { it in matching })
                }
                PerformanceFilter.EVER_INCORRECT -> {
                    val sql = """
                        SELECT qid FROM logs
                        WHERE selectedAnswer != corrAnswer
                        GROUP BY qid
                    """
                    val matching = mutableSetOf<Long>()
                    getConnection().prepare(sql).use { stmt ->
                        while (stmt.step()) {
                            matching.add(stmt.getLong(0))
                        }
                    }
                    result.addAll(qids.filter { it in matching })
                }
                PerformanceFilter.ALL -> { }
            }

            result
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection?.close()
            connection = null
        }
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
}
