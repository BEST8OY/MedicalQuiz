package com.medqb.app.shared.data.dao

import androidx.sqlite.SQLiteConnection
import com.medqb.app.shared.data.database.QuizSessionHistoryRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class HistoryDao(
    private val getConnection: () -> SQLiteConnection,
    private val mutex: Mutex,
) {
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
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                INSERT INTO quiz_history
                (session_id, database_name, entry_name, selected_subject_ids, selected_system_ids,
                 performance_filter, current_question_index, updated_at, is_logging_enabled, submission_mode)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(session_id) DO UPDATE SET
                    database_name = excluded.database_name,
                    entry_name = CASE WHEN excluded.entry_name = '' THEN quiz_history.entry_name ELSE excluded.entry_name END,
                    selected_subject_ids = excluded.selected_subject_ids,
                    selected_system_ids = excluded.selected_system_ids,
                    performance_filter = excluded.performance_filter,
                    current_question_index = excluded.current_question_index,
                    updated_at = excluded.updated_at,
                    is_logging_enabled = excluded.is_logging_enabled,
                    submission_mode = excluded.submission_mode
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

    suspend fun listHistoryEntries(): List<QuizSessionHistoryRow> = withContext(Dispatchers.IO) {
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

    suspend fun getHistoryEntry(sessionId: String): QuizSessionHistoryRow? = withContext(Dispatchers.IO) {
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

    suspend fun deleteHistoryEntries(sessionIds: List<String>) = withContext(Dispatchers.IO) {
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

    suspend fun renameHistoryEntry(sessionId: String, newName: String) = withContext(Dispatchers.IO) {
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
