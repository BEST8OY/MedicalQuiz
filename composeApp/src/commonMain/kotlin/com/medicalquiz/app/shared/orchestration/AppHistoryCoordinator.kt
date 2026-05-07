package com.medicalquiz.app.shared.orchestration

import com.medicalquiz.app.shared.data.DatabaseManager
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.Logger

class AppHistoryCoordinator(
    private val sessionRepository: QuizSessionRepository,
) {
    suspend fun restoreHistoryEntry(
        entry: QuizSessionRepository.QuizSession,
        availableDatabases: List<String>,
    ): String? {
        val matchingDatabase = availableDatabases.firstOrNull {
            it.removeSuffix(".db") == entry.databaseName
        } ?: return null

        if (sessionRepository.restoreHistoryEntryAsync(entry.id) == null) {
            return null
        }

        return matchingDatabase
    }

    suspend fun deleteHistoryEntriesWithLogs(
        entryIds: Set<String>,
        allHistoryEntries: List<QuizSessionRepository.QuizSession>,
        availableDatabases: List<String>,
    ) {
        if (entryIds.isEmpty()) return

        val selectedEntries = allHistoryEntries.filter { it.id in entryIds }
        require(selectedEntries.size == entryIds.size) {
            "Unable to resolve all selected history entries"
        }

        val databaseToSessionIds = selectedEntries
            .groupBy { it.databaseName }
            .mapValues { (_, entries) -> entries.map { it.id }.toSet() }

        for ((historyDatabaseName, sessionIds) in databaseToSessionIds) {
            val databaseFileName = availableDatabases.firstOrNull {
                it.removeSuffix(".db") == historyDatabaseName
            }
            if (databaseFileName == null) {
                Logger.w(
                    "AppHistoryCoordinator",
                    "Skipping log cleanup for deleted/missing database: $historyDatabaseName",
                )
                continue
            }

            val dbPath = FileSystemHelper.getDatabasePath(databaseFileName)
            val databaseManager = DatabaseManager(dbPath)
            try {
                databaseManager.init()
                databaseManager.clearLogsForSessions(sessionIds)
            } finally {
                databaseManager.closeDatabase()
            }
        }

        sessionRepository.deleteHistoryEntriesStrictAsync(entryIds)
    }

    suspend fun renameHistoryEntry(entryId: String, newName: String) {
        sessionRepository.renameHistoryEntryAsync(entryId, newName)
    }
}
