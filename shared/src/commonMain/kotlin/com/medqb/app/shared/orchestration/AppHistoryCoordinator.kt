package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.canonicalizeDbName
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
        val canonicalEntryDb = canonicalizeDbName(entry.databaseName)
        val availableDatabases = localContentRepository.listDatabases()
        val matchingDatabase = availableDatabases.firstOrNull {
            canonicalizeDbName(it) == canonicalEntryDb
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
        val activeDbName = active?.name?.let(::canonicalizeDbName).orEmpty()
        buildString {
            entries.forEach { entry ->
                if (canonicalizeDbName(entry.databaseName) != activeDbName) return@forEach
                val questionIds = db?.getQuestionIds(
                    subjectIds = entry.selectedSubjectIds,
                    excludedSubjectIds = entry.excludedSubjectIds,
                    systemIds = entry.selectedSystemIds,
                    excludedSystemIds = entry.excludedSystemIds,
                    statusFilter = entry.statusFilter,
                    difficultyFilters = entry.selectedDifficultyTiers,
                ) ?: emptyList()
                questionIds.forEach { qid ->
                    appendLine(qid)
                }
            }
        }
    }
}
