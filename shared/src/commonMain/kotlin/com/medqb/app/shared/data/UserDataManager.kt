package com.medqb.app.shared.data

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.local.dao.RoomSessionHistoryDao
import com.medqb.app.shared.data.local.dao.RoomTextHighlightDao
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
open class UserDataManager {
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
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
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

    open suspend fun init(): Unit = withContext(Dispatchers.IO) {
        getDatabase()
        Unit
    }

    open suspend fun logDao(): RoomLogDao = getDatabase().logDao()

    open suspend fun sessionHistoryDao(): RoomSessionHistoryDao = getDatabase().sessionHistoryDao()

    open suspend fun textHighlightDao(): RoomTextHighlightDao = getDatabase().textHighlightDao()

    /**
     * Runs [block] inside a single write transaction on the user database.
     */
    open suspend fun <R> withTransaction(block: suspend () -> R): R =
        withContext(Dispatchers.IO) {
            getDatabase().withWriteTransaction { block() }
        }

    open suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            database?.close()
            database = null
        }
    }
}

