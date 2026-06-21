package com.medqb.app.shared.data.dao

import androidx.sqlite.SQLiteConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class LogDao(
    private val getConnection: () -> SQLiteConnection,
    private val mutex: Mutex,
) {
    @OptIn(ExperimentalTime::class)
    suspend fun logAnswer(
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
                ensureSessionExists(sessionId)
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

    suspend fun clearLogForQuestion(qid: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            getConnection().prepare("DELETE FROM logs WHERE qid = ?").use { stmt ->
                stmt.bindLong(1, qid)
                stmt.step()
            }
            Unit
        }
    }

    fun ensureSessionLoggingSchema() {
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

    private fun ensureSessionExists(sessionId: String) {
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
