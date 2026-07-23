package com.medqb.app.shared.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.SessionHistoryDatabase
import com.medqb.app.shared.data.local.dao.RoomSessionHistoryDao
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.StorageProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Manages the session_history.db SQLite database via Room.
 */
@Inject
@SingleIn(AppScope::class)
class SessionHistoryManager {
    private var database: SessionHistoryDatabase? = null

    private val dbPath: String
        get() = "${StorageProvider.getAppStorageDirectory()}/session_history.db"

    fun getDatabase(): SessionHistoryDatabase {
        database?.let { return it }
        val db = Room.databaseBuilder<SessionHistoryDatabase>(dbPath)
            .setDriver(BundledSQLiteDriver())
            .build()
        database = db
        return db
    }

    fun sessionHistoryDao(): RoomSessionHistoryDao = getDatabase().sessionHistoryDao()
}
