package com.medicalquiz.app.shared.orchestration

import com.medicalquiz.app.shared.data.LocalContentRepository
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.platform.Logger

class AppHistoryCoordinator(
    private val sessionRepository: QuizSessionRepository,
    private val localContentRepository: LocalContentRepository,
) {
    suspend fun restoreHistoryEntry(
        entry: QuizSessionRepository.QuizSession,
    ): String? {
        Logger.d("AppHistoryCoordinator", "restoreHistoryEntry: id=${entry.id}, db=${entry.databaseName}")
        val availableDatabases = localContentRepository.listDatabases()
        Logger.d("AppHistoryCoordinator", "restoreHistoryEntry: availableDatabases=$availableDatabases")
        val matchingDatabase = availableDatabases.firstOrNull {
            it.removeSuffix(".db") == entry.databaseName
        }
        if (matchingDatabase == null) {
            Logger.w("AppHistoryCoordinator", "restoreHistoryEntry: no matching database for ${entry.databaseName}")
            return null
        }

        val restoredSession = sessionRepository.restoreHistoryEntryAsync(entry.id)
        Logger.d("AppHistoryCoordinator", "restoreHistoryEntry: restoredSession=${restoredSession?.id}")
        if (restoredSession == null) {
            Logger.w("AppHistoryCoordinator", "restoreHistoryEntry: restoreHistoryEntryAsync returned null")
            return null
        }

        return matchingDatabase
    }

    suspend fun deleteHistoryEntries(entryIds: Set<String>) {
        if (entryIds.isEmpty()) return
        sessionRepository.deleteHistoryEntriesStrictAsync(entryIds)
    }

    suspend fun renameHistoryEntry(entryId: String, newName: String) {
        sessionRepository.renameHistoryEntryAsync(entryId, newName)
    }
}
