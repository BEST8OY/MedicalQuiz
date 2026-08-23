package com.medqb.app.shared.data

import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.local.dao.RoomSessionHistoryDao
import com.medqb.app.shared.data.local.entity.TextHighlightEntity
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
import kotlin.concurrent.Volatile

/**
 * Owns the single user database (logs, text highlights, quiz sessions/history) via Room.
 *
 * All multi-statement writes that span DAOs (e.g. log insert + session link) must go
 * through [withTransaction] so they commit atomically — there is only one database now.
 */
@Inject
@SingleIn(AppScope::class)
class UserDataManager {
    private val mutex = Mutex()

    @Volatile
    private var database: UserDatabase? = null

    private val dbPath: String
        get() = "${StorageProvider.getAppStorageDirectory()}/user_data.db"

    private suspend fun getDatabase(): UserDatabase {
        database?.let { return it }
        return mutex.withLock {
            database?.let { return@withLock it }
            try {
                val db = Room.databaseBuilder<UserDatabase>(dbPath)
                    .setDriver(BundledSQLiteDriver())
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                database = db
                db
            } catch (e: Exception) {
                Logger.e("UserDataManager", "Error initializing user data database", e)
                throw e
            }
        }
    }

    suspend fun init() = withContext(Dispatchers.IO) {
        getDatabase()
    }

    suspend fun logDao(): RoomLogDao = getDatabase().logDao()

    suspend fun sessionHistoryDao(): RoomSessionHistoryDao = getDatabase().sessionHistoryDao()

    /**
     * Runs [block] inside a single write transaction on the user database.
     */
    suspend fun <R> withTransaction(block: suspend () -> R): R =
        withContext(Dispatchers.IO) {
            getDatabase().withWriteTransaction { block() }
        }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            database?.close()
            database = null
        }
    }

    suspend fun getTextHighlights(dbName: String, questionId: Long, section: HighlightSection): List<TextHighlight> =
        withContext(Dispatchers.IO) {
            getDatabase().textHighlightDao()
                .getBySection(dbName, questionId, section.name)
                .map { it.toDomain() }
        }

    suspend fun getAllTextHighlightsForQuestion(dbName: String, questionId: Long): List<TextHighlight> =
        withContext(Dispatchers.IO) {
            getDatabase().textHighlightDao()
                .getAllForQuestion(dbName, questionId)
                .map { it.toDomain() }
        }

    suspend fun addTextHighlight(
        dbName: String, questionId: Long, section: HighlightSection,
        startOffset: Int, endOffset: Int, highlightedText: String, color: HighlightColor
    ): TextHighlight = withContext(Dispatchers.IO) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val entity = TextHighlightEntity(
            dbName = dbName,
            questionId = questionId,
            section = section.name,
            startOffset = startOffset,
            endOffset = endOffset,
            highlightedText = highlightedText,
            color = color.name,
            createdAt = now
        )
        val insertedId = getDatabase().textHighlightDao().insert(entity)
        entity.copy(id = insertedId).toDomain()
    }

    suspend fun removeTextHighlight(highlightId: Long) = withContext(Dispatchers.IO) {
        getDatabase().textHighlightDao().deleteById(highlightId)
    }

    suspend fun updateTextHighlightColor(highlightId: Long, color: HighlightColor) =
        withContext(Dispatchers.IO) {
            getDatabase().textHighlightDao().updateColor(highlightId, color.name)
        }

    suspend fun clearTextHighlightsForQuestion(dbName: String, questionId: Long, section: HighlightSection? = null) =
        withContext(Dispatchers.IO) {
            getDatabase().textHighlightDao().clearForQuestion(dbName, questionId, section?.name)
        }

    suspend fun clearAllTextHighlightsForDatabase(dbName: String) =
        withContext(Dispatchers.IO) {
            getDatabase().textHighlightDao().clearForDatabase(dbName)
        }

    suspend fun replaceTextHighlightsWithMerged(
        dbName: String, questionId: Long, section: HighlightSection,
        removeHighlightIds: List<Long>, startOffset: Int, endOffset: Int,
        highlightedText: String, color: HighlightColor
    ): TextHighlight = withContext(Dispatchers.IO) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val insertEntity = TextHighlightEntity(
            dbName = dbName,
            questionId = questionId,
            section = section.name,
            startOffset = startOffset,
            endOffset = endOffset,
            highlightedText = highlightedText,
            color = color.name,
            createdAt = now
        )
        val insertedId = getDatabase().textHighlightDao()
            .replaceWithMerged(removeHighlightIds, insertEntity)
        insertEntity.copy(id = insertedId).toDomain()
    }

    private fun TextHighlightEntity.toDomain() = TextHighlight(
        id = id,
        dbName = dbName,
        questionId = questionId,
        section = HighlightSection.valueOf(section),
        startOffset = startOffset,
        endOffset = endOffset,
        highlightedText = highlightedText,
        color = HighlightColor.fromName(color),
        createdAt = createdAt
    )
}
