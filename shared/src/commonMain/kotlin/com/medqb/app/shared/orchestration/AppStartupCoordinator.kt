package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.DatabaseManager
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.data.canonicalizeDbName
import com.medqb.app.shared.platform.FileSystemHelper
import com.medqb.app.shared.platform.Logger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException

/**
 * Coordinates app startup routines: listings available DBs, handling DB selections.
 */
@Inject
open class AppStartupCoordinator(
    private val localContentRepository: LocalContentRepository,
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val userDataManager: UserDataManager,
) {
    open suspend fun initializeApp(): List<String> {
        userDataManager.init()
        return localContentRepository.listDatabases()
    }

    open suspend fun initializeApp(userDataManager: UserDataManager): List<String> = initializeApp()

    open suspend fun refreshDatabases(): List<String> {
        userDataManager.init()
        return localContentRepository.listDatabases()
    }

    open suspend fun closeActiveDatabase() {
        activeDatabaseHolder.closeDatabase()
    }

    open suspend fun handleDatabaseSelection(
        selectedDatabase: String?,
        userDataManager: UserDataManager,
    ): DatabaseSelectionDecision? {
        val dbName = selectedDatabase ?: return null
        return try {
            val resolvedDatabase = ensureDatabaseInitialized(dbName, userDataManager)
            DatabaseSelectionDecision(
                initializedDatabase = resolvedDatabase,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("AppStartupCoordinator", "Failed to open database $dbName", e)
            activeDatabaseHolder.reportOpenFailure(dbName, e)
            null
        }
    }

    private suspend fun ensureDatabaseInitialized(dbName: String, userDataManager: UserDataManager): String {
        activeDatabaseHolder.clearOpenFailure()
        val current = activeDatabaseHolder.activeDatabase.value
        // Single atomic snapshot — name and connection can never disagree here.
        if (current != null && current.matchesFileName(dbName)) return dbName

        val dbPath = FileSystemHelper.getDatabasePath(dbName)
        val databaseManager = DatabaseManager(dbPath, canonicalizeDbName(dbName), userDataManager)
        databaseManager.init()

        activeDatabaseHolder.setDatabase(dbName, databaseManager)
        return dbName
    }
}

data class DatabaseSelectionDecision(
    val initializedDatabase: String,
)
