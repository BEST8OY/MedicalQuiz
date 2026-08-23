package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.DatabaseManager
import com.medqb.app.shared.data.LocalContentRepository
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
    private val activeDatabaseHolder: ActiveDatabaseHolder,
) {
    suspend fun initializeApp(userDataManager: UserDataManager): List<String> {
        userDataManager.init()
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

    private suspend fun ensureDatabaseInitialized(dbName: String, userDataManager: UserDataManager): String {
        val current = activeDatabaseHolder.activeDatabase.value
        // Single atomic snapshot — name and connection can never disagree here.
        if (current != null && current.matchesFileName(dbName)) return dbName

        val dbPath = FileSystemHelper.getDatabasePath(dbName)
        val databaseManager = DatabaseManager(dbPath, dbName.removeSuffix(".db"), userDataManager)
        databaseManager.init()

        activeDatabaseHolder.setDatabase(dbName, databaseManager)
        return dbName
    }
}

data class DatabaseSelectionDecision(
    val initializedDatabase: String,
    val pendingLaunchSource: QuizLaunchSource?,
)
