package com.medicalquiz.app.shared.domain

import com.medicalquiz.app.shared.data.ActiveDatabaseHolder
import com.medicalquiz.app.shared.data.DatabaseManager
import com.medicalquiz.app.shared.data.LogsManager
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.navigation.QuizLaunchSource
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.StorageProvider

class RestoreSessionUseCase(
    private val activeDatabaseHolder: ActiveDatabaseHolder,
    private val sessionRepository: QuizSessionRepository
) {
    private var logsManager: LogsManager? = null

    suspend fun initLogsManager(): LogsManager {
        logsManager?.let { return it }
        val manager = LogsManager("${StorageProvider.getAppStorageDirectory()}/logs.db")
        manager.init()
        logsManager = manager
        return manager
    }

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
            return if (activeSession != null && activeSession.databaseName == dbName.removeSuffix(".db")) {
                RestoreSessionDecision(
                    initializedDatabase = resolvedInitializedDatabase,
                    pendingLaunchSource = null,
                    shouldAttemptSessionRestore = shouldAttemptSessionRestore,
                    shouldPopToDatabaseSelection = false,
                )
            } else {
                RestoreSessionDecision(
                    initializedDatabase = resolvedInitializedDatabase,
                    pendingLaunchSource = null,
                    shouldAttemptSessionRestore = shouldAttemptSessionRestore,
                    shouldPopToDatabaseSelection = true,
                )
            }
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

        val logs = initLogsManager()
        activeDatabaseHolder.setDatabase(dbName.removeSuffix(".db"), databaseManager, logs)
        return dbName
    }
}

data class RestoreSessionDecision(
    val initializedDatabase: String,
    val pendingLaunchSource: QuizLaunchSource?,
    val shouldAttemptSessionRestore: Boolean,
    val shouldPopToDatabaseSelection: Boolean,
)
