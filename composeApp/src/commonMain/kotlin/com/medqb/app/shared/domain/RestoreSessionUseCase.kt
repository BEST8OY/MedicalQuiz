package com.medqb.app.shared.domain

import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.DatabaseManager
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.navigation.QuizLaunchSource
import com.medqb.app.shared.platform.FileSystemHelper

/**
 * UseCase to handle SQLite database connection setup and active session verification.
 */
class RestoreSessionUseCase(
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val sessionRepository: QuizSessionRepository
) {

    suspend operator fun invoke(
        dbName: String,
        initializedDatabase: String?,
        pendingLaunchSource: QuizLaunchSource?,
        shouldAttemptSessionRestore: Boolean,
    ): RestoreSessionDecision {
        val resolvedInitializedDatabase = ensureDatabaseInitialized(
            dbName = dbName,
            initializedDatabase = initializedDatabase
        )

        val activeSession = sessionRepository.restoreSessionAsync()

        if (pendingLaunchSource == QuizLaunchSource.History) {
            return RestoreSessionDecision(
                initializedDatabase = resolvedInitializedDatabase,
                pendingLaunchSource = null,
                shouldAttemptSessionRestore = shouldAttemptSessionRestore,
                shouldPopToDatabaseSelection = false,
            )
        }

        if (shouldAttemptSessionRestore) {
            return if (activeSession != null && activeSession.databaseName == dbName.removeSuffix(".db")) {
                RestoreSessionDecision(
                    initializedDatabase = resolvedInitializedDatabase,
                    pendingLaunchSource = pendingLaunchSource,
                    shouldAttemptSessionRestore = false,
                    shouldPopToDatabaseSelection = false,
                )
            } else {
                RestoreSessionDecision(
                    initializedDatabase = resolvedInitializedDatabase,
                    pendingLaunchSource = pendingLaunchSource,
                    shouldAttemptSessionRestore = false,
                    shouldPopToDatabaseSelection = true,
                )
            }
        }

        return RestoreSessionDecision(
            initializedDatabase = resolvedInitializedDatabase,
            pendingLaunchSource = pendingLaunchSource,
            shouldAttemptSessionRestore = shouldAttemptSessionRestore,
            shouldPopToDatabaseSelection = false,
        )
    }

    private suspend fun ensureDatabaseInitialized(
        dbName: String,
        initializedDatabase: String?,
    ): String {
        val hasDatabaseManager = activeDatabaseHolder.databaseProvider.value != null
        if (initializedDatabase == dbName && hasDatabaseManager) return dbName

        val dbPath = FileSystemHelper.getDatabasePath(dbName)
        val databaseManager = DatabaseManager(dbPath)
        databaseManager.init()

        activeDatabaseHolder.setDatabase(dbName.removeSuffix(".db"), databaseManager)
        return dbName
    }
}

data class RestoreSessionDecision(
    val initializedDatabase: String,
    val pendingLaunchSource: QuizLaunchSource?,
    val shouldAttemptSessionRestore: Boolean,
    val shouldPopToDatabaseSelection: Boolean,
)
