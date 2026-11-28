package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.DatabaseManager
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.ManagedDatabaseProvider
import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.data.canonicalizeDbName
import com.medqb.app.shared.platform.FileSystemHelper
import com.medqb.app.shared.platform.Logger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates app startup routines: listing available DBs and serializing DB open/close lifecycle.
 */
@Inject
open class AppStartupCoordinator(
    private val localContentRepository: LocalContentRepository,
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val userDataManager: UserDataManager,
) {
    private val dbLifecycleMutex = Mutex()

    open suspend fun initializeApp(): List<String> {
        userDataManager.init()
        return localContentRepository.listDatabases()
    }

    open suspend fun refreshDatabases(): List<String> {
        userDataManager.init()
        return localContentRepository.listDatabases()
    }

    open suspend fun closeActiveDatabase() {
        withContext(NonCancellable) {
            dbLifecycleMutex.withLock {
                activeDatabaseHolder.closeDatabase()
            }
        }
    }

    open suspend fun handleDatabaseSelection(
        selectedDatabase: String?,
    ): DatabaseSelectionDecision? {
        val dbName = selectedDatabase ?: return null
        return try {
            val resolvedDatabase = dbLifecycleMutex.withLock {
                ensureDatabaseInitializedLocked(dbName)
            }
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

    internal open fun createDatabaseManager(
        dbPath: String,
        canonicalDbName: String,
    ): ManagedDatabaseProvider = DatabaseManager(dbPath, canonicalDbName, userDataManager)

    private suspend fun ensureDatabaseInitializedLocked(dbName: String): String {
        activeDatabaseHolder.clearOpenFailure()
        val current = activeDatabaseHolder.activeDatabase.value
        // Single atomic snapshot — name and connection can never disagree here.
        if (current != null && current.matchesFileName(dbName)) return dbName

        if (current != null) {
            activeDatabaseHolder.closeDatabase()
        }

        val dbPath = FileSystemHelper.getDatabasePath(dbName)
        val databaseManager = createDatabaseManager(dbPath, canonicalizeDbName(dbName))
        try {
            databaseManager.init()
            activeDatabaseHolder.setDatabase(dbName, databaseManager)
        } catch (e: Throwable) {
            if (activeDatabaseHolder.activeDatabase.value?.provider !== databaseManager) {
                withContext(NonCancellable) {
                    try {
                        databaseManager.closeDatabase()
                    } catch (closeEx: Exception) {
                        Logger.e("AppStartupCoordinator", "Error closing aborted database $dbName", closeEx)
                    }
                }
            }
            throw e
        }
        return dbName
    }
}

data class DatabaseSelectionDecision(
    val initializedDatabase: String,
)
