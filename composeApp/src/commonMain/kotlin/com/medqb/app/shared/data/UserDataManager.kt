package com.medqb.app.shared.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.dao.TextHighlightDao
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.platform.StorageProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages the user_data.db SQLite database connection.
 * Text highlights CRUD is delegated to [TextHighlightDao].
 */
@Inject
@SingleIn(AppScope::class)
class UserDataManager {
    private val driver = BundledSQLiteDriver()
    private var connection: SQLiteConnection? = null
    private val mutex = Mutex()

    val textHighlightDao: TextHighlightDao

    private val dbPath: String
        get() = "${StorageProvider.getAppStorageDirectory()}/user_data.db"

    init {
        textHighlightDao = TextHighlightDao({ getConnection() }, mutex)
    }

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

    // Delegate text highlights to TextHighlightDao
    suspend fun getTextHighlights(dbName: String, questionId: Long, section: HighlightSection): List<TextHighlight> =
        textHighlightDao.getTextHighlights(dbName, questionId, section)

    suspend fun getAllTextHighlightsForQuestion(dbName: String, questionId: Long): List<TextHighlight> =
        textHighlightDao.getAllTextHighlightsForQuestion(dbName, questionId)

    suspend fun addTextHighlight(
        dbName: String, questionId: Long, section: HighlightSection,
        startOffset: Int, endOffset: Int, highlightedText: String, color: HighlightColor
    ): TextHighlight = textHighlightDao.addTextHighlight(dbName, questionId, section, startOffset, endOffset, highlightedText, color)

    suspend fun removeTextHighlight(highlightId: Long) = textHighlightDao.removeTextHighlight(highlightId)

    suspend fun updateTextHighlightColor(highlightId: Long, color: HighlightColor) =
        textHighlightDao.updateTextHighlightColor(highlightId, color)

    suspend fun clearTextHighlightsForQuestion(dbName: String, questionId: Long, section: HighlightSection? = null) =
        textHighlightDao.clearTextHighlightsForQuestion(dbName, questionId, section)

    suspend fun clearAllTextHighlightsForDatabase(dbName: String) =
        textHighlightDao.clearAllTextHighlightsForDatabase(dbName)

    suspend fun replaceTextHighlightsWithMerged(
        dbName: String, questionId: Long, section: HighlightSection,
        removeHighlightIds: List<Long>, startOffset: Int, endOffset: Int,
        highlightedText: String, color: HighlightColor
    ): TextHighlight = textHighlightDao.replaceTextHighlightsWithMerged(
        dbName, questionId, section, removeHighlightIds, startOffset, endOffset, highlightedText, color
    )
}
