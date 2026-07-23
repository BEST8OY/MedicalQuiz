package com.medqb.app.shared.orchestration

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.DatabaseManager
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.data.local.SessionHistoryDatabase
import com.medqb.app.shared.navigation.QuizLaunchSource
import com.medqb.app.shared.platform.FileSystemHelper
import com.medqb.app.shared.platform.StorageProvider
import dev.zacsweers.metro.Inject

/**
 * Coordinates app startup routines: listings available DBs, handling DB selections.
 */
@Inject
class AppStartupCoordinator(
    private val localContentRepository: LocalContentRepository,
    private val activeDatabaseHolder: ActiveDatabaseHolder,
) {
    private var sessionHistoryDatabase: SessionHistoryDatabase? = null

    private val sessionHistoryDbPath: String
        get() = "${StorageProvider.getAppStorageDirectory()}/session_history.db"

    suspend fun initializeApp(userDataManager: UserDataManager): List<String> {
        userDataManager.init()
        initSessionHistoryDatabase()
        return localContentRepository.listDatabases()
    }

    suspend fun refreshDatabases(): List<String> = localContentRepository.listDatabases()

    suspend fun handleDatabaseSelection(
        selectedDatabase: String?,
        initializedDatabase: String?,
        pendingLaunchSource: QuizLaunchSource?,
        userDataManager: UserDataManager,
    ): DatabaseSelectionDecision? {
        val dbName = selectedDatabase ?: return null
        val resolvedDatabase = ensureDatabaseInitialized(dbName, userDataManager)

        return DatabaseSelectionDecision(
            initializedDatabase = resolvedDatabase,
            pendingLaunchSource = pendingLaunchSource,
        )
    }

    private fun initSessionHistoryDatabase() {
        if (sessionHistoryDatabase != null) return
        sessionHistoryDatabase = Room.databaseBuilder<SessionHistoryDatabase>(sessionHistoryDbPath)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    private suspend fun ensureDatabaseInitialized(dbName: String, userDataManager: UserDataManager): String {
        val currentName = activeDatabaseHolder.databaseName.value
        val currentProvider = activeDatabaseHolder.databaseProvider.value
        if (currentName == dbName.removeSuffix(".db") && currentProvider != null) return dbName

        initSessionHistoryDatabase()
        val dbPath = FileSystemHelper.getDatabasePath(dbName)
        val sessionHistoryDao = sessionHistoryDatabase!!.sessionHistoryDao()
        val roomLogDao = userDataManager.logDao()
        val databaseManager = DatabaseManager(dbPath, dbName.removeSuffix(".db"), sessionHistoryDao, roomLogDao)
        databaseManager.init()

        activeDatabaseHolder.setDatabase(dbName.removeSuffix(".db"), databaseManager)
        return dbName
    }
}

data class DatabaseSelectionDecision(
    val initializedDatabase: String,
    val pendingLaunchSource: QuizLaunchSource?,
)
