package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.QuizSessionRepository
import dev.zacsweers.metro.Inject

@Inject
class AppHistoryCoordinator(
    private val sessionRepository: QuizSessionRepository,
    private val localContentRepository: LocalContentRepository,
) {
    suspend fun restoreHistoryEntry(
        entry: QuizSessionRepository.QuizSession,
    ): String? {
        val availableDatabases = localContentRepository.listDatabases()
        val matchingDatabase = availableDatabases.firstOrNull {
            it.removeSuffix(".db") == entry.databaseName
        } ?: return null

        if (sessionRepository.restoreHistoryEntryAsync(entry.id) == null) {
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
