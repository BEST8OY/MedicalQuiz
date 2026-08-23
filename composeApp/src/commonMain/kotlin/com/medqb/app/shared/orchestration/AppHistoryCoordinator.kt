package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.QuizSessionRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Inject
class AppHistoryCoordinator(
    private val sessionRepository: QuizSessionRepository,
    private val localContentRepository: LocalContentRepository,
    private val activeDatabaseHolder: ActiveDatabaseHolder,
) {
    suspend fun restoreHistoryEntry(
        entry: QuizSessionRepository.QuizSession,
    ): String? {
        val availableDatabases = localContentRepository.listDatabases()
        val matchingDatabase = availableDatabases.firstOrNull {
            it.removeSuffix(".db") == entry.databaseName
        } ?: return null

        if (sessionRepository.restoreHistoryEntry(entry.id) == null) {
            return null
        }

        return matchingDatabase
    }

    suspend fun deleteHistoryEntries(entryIds: Set<String>) {
        if (entryIds.isEmpty()) return
        sessionRepository.deleteHistoryEntries(entryIds)
    }

    suspend fun renameHistoryEntry(entryId: String, newName: String) {
        sessionRepository.renameHistoryEntry(entryId, newName)
    }

    suspend fun getQuestionIdsForHistoryEntries(
        entries: List<QuizSessionRepository.QuizSession>,
    ): String = withContext(Dispatchers.IO) {
        // One atomic snapshot — the connection and its name always agree.
        val active = activeDatabaseHolder.activeDatabase.value
        val db = active?.provider
        val activeDbName = active?.name.orEmpty()
        buildString {
            entries.forEach { entry ->
                if (entry.databaseName != activeDbName) return@forEach
                val questionIds = db?.getQuestionIds(
                    subjectIds = entry.selectedSubjectIds,
                    systemIds = entry.selectedSystemIds,
                    performanceFilter = entry.performanceFilter,
                ) ?: emptyList()
                questionIds.forEach { qid ->
                    appendLine(qid)
                }
            }
        }
    }
}
