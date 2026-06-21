package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.DatabaseManager
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.navigation.QuizLaunchSource
import com.medqb.app.shared.platform.FileSystemHelper
import dev.zacsweers.metro.Inject

/**
 * Coordinates app startup routines: listings available DBs, handling DB selections.
 */
@Inject
class AppStartupCoordinator(
    private val localContentRepository: LocalContentRepository,
    private val sessionRepository: QuizSessionRepository,
    private val activeDatabaseHolder: ActiveDatabaseHolder,
) {
    suspend fun initializeApp(userDataManager: UserDataManager): List<String> {
        userDataManager.init()
        val databases = localContentRepository.listDatabases()
        sessionRepository.refreshHistoryAsync()
        return databases
    }

    suspend fun refreshDatabases(): List<String> = localContentRepository.listDatabases()

    suspend fun handleDatabaseSelection(
        selectedDatabase: String?,
        initializedDatabase: String?,
        pendingLaunchSource: QuizLaunchSource?,
    ): DatabaseSelectionDecision? {
        val dbName = selectedDatabase ?: return null
        val resolvedDatabase = ensureDatabaseInitialized(dbName)

        return DatabaseSelectionDecision(
            initializedDatabase = resolvedDatabase,
            pendingLaunchSource = pendingLaunchSource,
        )
    }

    private suspend fun ensureDatabaseInitialized(dbName: String): String {
        val currentName = activeDatabaseHolder.databaseName.value
        val currentProvider = activeDatabaseHolder.databaseProvider.value
        if (currentName == dbName.removeSuffix(".db") && currentProvider != null) return dbName

        val dbPath = FileSystemHelper.getDatabasePath(dbName)
        val databaseManager = DatabaseManager(dbPath)
        databaseManager.init()

        activeDatabaseHolder.setDatabase(dbName.removeSuffix(".db"), databaseManager)
        return dbName
    }
}

data class DatabaseSelectionDecision(
    val initializedDatabase: String,
    val pendingLaunchSource: QuizLaunchSource?,
)
