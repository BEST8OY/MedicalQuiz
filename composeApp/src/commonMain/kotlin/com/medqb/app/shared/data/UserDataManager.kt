package com.medqb.app.shared.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.platform.StorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages the user_data.db SQLite database for storing personal data
 * like text highlights and notes that persist across question databases.
 */
class UserDataManager {
    private val driver = BundledSQLiteDriver()
    private var connection: SQLiteConnection? = null
    private val mutex = Mutex()

    private val dbPath: String
        get() = "${StorageProvider.getAppStorageDirectory()}/user_data.db"

    suspend fun init() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                getConnection()
            } catch (e: Exception) {
                Logger.e("UserDataManager", "Error initializing user data database", e)
                throw e
            }
        }
    }

    private fun createTables(conn: SQLiteConnection) {
        // Create text_highlights table (text range highlights within questions)
        conn.prepare("""
            CREATE TABLE IF NOT EXISTS text_highlights (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                db_name TEXT NOT NULL,
                question_id INTEGER NOT NULL,
                section TEXT NOT NULL,
                start_offset INTEGER NOT NULL,
                end_offset INTEGER NOT NULL,
                highlighted_text TEXT NOT NULL,
                color TEXT NOT NULL DEFAULT 'YELLOW',
                created_at INTEGER NOT NULL
            )
        """.trimIndent()).use { it.step() }

        // Create index for text highlights lookup
        conn.prepare("""
            CREATE INDEX IF NOT EXISTS idx_text_highlights_lookup 
            ON text_highlights(db_name, question_id, section)
        """.trimIndent()).use { it.step() }
    }

    private fun getConnection(): SQLiteConnection {
        connection?.let { return it }

        val newConnection = driver.open(dbPath)
        return try {
            createTables(newConnection)
            connection = newConnection
            newConnection
        } catch (e: Exception) {
            runCatching { newConnection.close() }
            throw e
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection?.close()
            connection = null
        }
    }

    // ==================== Text Highlights CRUD ====================

    suspend fun getTextHighlights(
        dbName: String,
        questionId: Long,
        section: HighlightSection
    ): List<TextHighlight> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                SELECT id, db_name, question_id, section, start_offset, end_offset, 
                       highlighted_text, color, created_at 
                FROM text_highlights 
                WHERE db_name = ? AND question_id = ? AND section = ?
                ORDER BY start_offset
            """
            
            val highlights = mutableListOf<TextHighlight>()
            getConnection().prepare(sql).use { stmt ->
                stmt.bindText(1, dbName)
                stmt.bindLong(2, questionId)
                stmt.bindText(3, section.name)
                while (stmt.step()) {
                    highlights.add(
                        TextHighlight(
                            id = stmt.getLong(0),
                            dbName = stmt.getText(1),
                            questionId = stmt.getLong(2),
                            section = HighlightSection.valueOf(stmt.getText(3)),
                            startOffset = stmt.getLong(4).toInt(),
                            endOffset = stmt.getLong(5).toInt(),
                            highlightedText = stmt.getText(6),
                            color = HighlightColor.fromName(stmt.getText(7)),
                            createdAt = stmt.getLong(8)
                        )
                    )
                }
            }
            highlights
        }
    }

    suspend fun getAllTextHighlightsForQuestion(
        dbName: String,
        questionId: Long
    ): List<TextHighlight> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                SELECT id, db_name, question_id, section, start_offset, end_offset, 
                       highlighted_text, color, created_at 
                FROM text_highlights 
                WHERE db_name = ? AND question_id = ?
                ORDER BY section, start_offset
            """
            
            val highlights = mutableListOf<TextHighlight>()
            getConnection().prepare(sql).use { stmt ->
                stmt.bindText(1, dbName)
                stmt.bindLong(2, questionId)
                while (stmt.step()) {
                    highlights.add(
                        TextHighlight(
                            id = stmt.getLong(0),
                            dbName = stmt.getText(1),
                            questionId = stmt.getLong(2),
                            section = HighlightSection.valueOf(stmt.getText(3)),
                            startOffset = stmt.getLong(4).toInt(),
                            endOffset = stmt.getLong(5).toInt(),
                            highlightedText = stmt.getText(6),
                            color = HighlightColor.fromName(stmt.getText(7)),
                            createdAt = stmt.getLong(8)
                        )
                    )
                }
            }
            highlights
        }
    }

    suspend fun addTextHighlight(
        dbName: String,
        questionId: Long,
        section: HighlightSection,
        startOffset: Int,
        endOffset: Int,
        highlightedText: String,
        color: HighlightColor
    ): TextHighlight = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            
            val sql = """
                INSERT INTO text_highlights 
                (db_name, question_id, section, start_offset, end_offset, highlighted_text, color, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """
            
            getConnection().prepare(sql).use { stmt ->
                stmt.bindText(1, dbName)
                stmt.bindLong(2, questionId)
                stmt.bindText(3, section.name)
                stmt.bindLong(4, startOffset.toLong())
                stmt.bindLong(5, endOffset.toLong())
                stmt.bindText(6, highlightedText)
                stmt.bindText(7, color.name)
                stmt.bindLong(8, now)
                stmt.step()
            }

            // Get the inserted ID
            var insertedId = 0L
            getConnection().prepare("SELECT last_insert_rowid()").use { stmt ->
                if (stmt.step()) {
                    insertedId = stmt.getLong(0)
                }
            }

            TextHighlight(
                id = insertedId,
                dbName = dbName,
                questionId = questionId,
                section = section,
                startOffset = startOffset,
                endOffset = endOffset,
                highlightedText = highlightedText,
                color = color,
                createdAt = now
            )
        }
    }

    suspend fun removeTextHighlight(highlightId: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = "DELETE FROM text_highlights WHERE id = ?"
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, highlightId)
                stmt.step()
            }
            Unit
        }
    }

    suspend fun updateTextHighlightColor(highlightId: Long, color: HighlightColor) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = "UPDATE text_highlights SET color = ? WHERE id = ?"
            getConnection().prepare(sql).use { stmt ->
                stmt.bindText(1, color.name)
                stmt.bindLong(2, highlightId)
                stmt.step()
            }
            Unit
        }
    }

    suspend fun clearTextHighlightsForQuestion(
        dbName: String,
        questionId: Long,
        section: HighlightSection? = null
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = if (section != null) {
                "DELETE FROM text_highlights WHERE db_name = ? AND question_id = ? AND section = ?"
            } else {
                "DELETE FROM text_highlights WHERE db_name = ? AND question_id = ?"
            }
            
            getConnection().prepare(sql).use { stmt ->
                stmt.bindText(1, dbName)
                stmt.bindLong(2, questionId)
                if (section != null) {
                    stmt.bindText(3, section.name)
                }
                stmt.step()
            }
            Unit
        }
    }

    suspend fun clearAllTextHighlightsForDatabase(dbName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = "DELETE FROM text_highlights WHERE db_name = ?"
            getConnection().prepare(sql).use { stmt ->
                stmt.bindText(1, dbName)
                stmt.step()
            }
            Unit
        }
    }

    suspend fun replaceTextHighlightsWithMerged(
        dbName: String,
        questionId: Long,
        section: HighlightSection,
        removeHighlightIds: List<Long>,
        startOffset: Int,
        endOffset: Int,
        highlightedText: String,
        color: HighlightColor
    ): TextHighlight = withContext(Dispatchers.IO) {
        mutex.withLock {
            val conn = getConnection()
            val now = System.currentTimeMillis()
            var insertedId = 0L

            conn.prepare("BEGIN IMMEDIATE").use { it.step() }
            try {
                if (removeHighlightIds.isNotEmpty()) {
                    conn.prepare("DELETE FROM text_highlights WHERE id = ?").use { deleteStmt ->
                        removeHighlightIds.forEach { highlightId ->
                            deleteStmt.bindLong(1, highlightId)
                            deleteStmt.step()
                            deleteStmt.reset()
                        }
                    }
                }

                conn.prepare(
                    """
                    INSERT INTO text_highlights
                    (db_name, question_id, section, start_offset, end_offset, highlighted_text, color, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { insertStmt ->
                    insertStmt.bindText(1, dbName)
                    insertStmt.bindLong(2, questionId)
                    insertStmt.bindText(3, section.name)
                    insertStmt.bindLong(4, startOffset.toLong())
                    insertStmt.bindLong(5, endOffset.toLong())
                    insertStmt.bindText(6, highlightedText)
                    insertStmt.bindText(7, color.name)
                    insertStmt.bindLong(8, now)
                    insertStmt.step()
                }

                conn.prepare("SELECT last_insert_rowid()").use { rowIdStmt ->
                    if (rowIdStmt.step()) {
                        insertedId = rowIdStmt.getLong(0)
                    }
                }

                conn.prepare("COMMIT").use { it.step() }
            } catch (e: Exception) {
                try {
                    conn.prepare("ROLLBACK").use { it.step() }
                } catch (_: Exception) {
                }
                throw e
            }

            TextHighlight(
                id = insertedId,
                dbName = dbName,
                questionId = questionId,
                section = section,
                startOffset = startOffset,
                endOffset = endOffset,
                highlightedText = highlightedText,
                color = color,
                createdAt = now
            )
        }
    }
}
