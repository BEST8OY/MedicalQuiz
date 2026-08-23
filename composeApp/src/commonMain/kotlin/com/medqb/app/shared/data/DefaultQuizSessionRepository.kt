package com.medqb.app.shared.data

import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.local.entity.QuizHistoryEntity
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Room-backed [QuizSessionRepository] over the shared user database.
 */
@Inject
@SingleIn(AppScope::class)
class DefaultQuizSessionRepository(
    private val userDataManager: UserDataManager,
) : QuizSessionRepository {
    // Process-scoped: intentionally not cancelled — survives config changes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val historyEntries: StateFlow<List<QuizSessionRepository.QuizSession>> = flow {
        emitAll(userDataManager.sessionHistoryDao().listHistory())
    }
        .map { entities -> entities.map { it.toQuizSession() } }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    override suspend fun listHistory(): List<QuizSessionRepository.QuizSession> =
        withContext(Dispatchers.IO) {
            userDataManager.sessionHistoryDao().listHistoryOnce().map { it.toQuizSession() }
        }

    override suspend fun appendToHistory(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
        currentQuestionIndex: Int,
        isLoggingEnabled: Boolean,
        submissionMode: SubmissionMode,
        currentSessionId: String,
        entryName: String,
    ): String {
        if (databaseName.isBlank()) {
            Logger.e("QuizSession", "Cannot append history: databaseName is blank")
            return ""
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val sessionId = if (currentSessionId.isNotBlank()) {
            currentSessionId
        } else {
            buildSessionId(databaseName, now)
        }

        withContext(Dispatchers.IO) {
            userDataManager.sessionHistoryDao().upsertHistory(
                sessionId = sessionId,
                databaseName = databaseName,
                entryName = entryName,
                selectedSubjectIds = selectedSubjectIds.toSortedSet().joinToString(","),
                selectedSystemIds = selectedSystemIds.toSortedSet().joinToString(","),
                performanceFilter = performanceFilter.name,
                currentQuestionIndex = currentQuestionIndex,
                updatedAt = now,
                isLoggingEnabled = isLoggingEnabled,
                submissionMode = submissionMode.name,
            )
        }

        return sessionId
    }

    override suspend fun deleteHistoryEntries(entryIds: Set<String>) = withContext(Dispatchers.IO) {
        if (entryIds.isEmpty()) return@withContext
        val dao = userDataManager.sessionHistoryDao()
        dao.deleteHistory(entryIds.toList())
        dao.deleteOrphanedSessions()
    }

    override suspend fun renameHistoryEntry(entryId: String, newName: String) = withContext(Dispatchers.IO) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return@withContext
        userDataManager.sessionHistoryDao().renameHistory(entryId, trimmedName)
    }

    override suspend fun restoreDeletedHistoryEntry(entry: QuizSessionRepository.QuizSession) =
        withContext(Dispatchers.IO) {
            userDataManager.sessionHistoryDao().upsertHistory(
                sessionId = entry.id,
                databaseName = entry.databaseName,
                entryName = entry.entryName,
                selectedSubjectIds = entry.selectedSubjectIds.joinToString(","),
                selectedSystemIds = entry.selectedSystemIds.joinToString(","),
                performanceFilter = entry.performanceFilter.name,
                currentQuestionIndex = entry.currentQuestionIndex,
                updatedAt = entry.updatedAtEpochMillis,
                isLoggingEnabled = entry.isLoggingEnabled,
                submissionMode = entry.submissionMode.name,
            )
        }

    override suspend fun restoreHistoryEntry(entryId: String): QuizSessionRepository.QuizSession? =
        withContext(Dispatchers.IO) {
            userDataManager.sessionHistoryDao().getHistory(entryId)?.toQuizSession()
        }

    private fun buildSessionId(databaseName: String, now: Long): String = "$databaseName-$now"
}

private fun QuizHistoryEntity.toQuizSession() = QuizSessionRepository.QuizSession(
    id = sessionId,
    databaseName = databaseName,
    entryName = entryName,
    selectedSubjectIds = selectedSubjectIds.split(",").mapNotNull { it.trim().trim('[', ']', ' ').toLongOrNull() },
    selectedSystemIds = selectedSystemIds.split(",").mapNotNull { it.trim().trim('[', ']', ' ').toLongOrNull() },
    performanceFilter = runCatching { PerformanceFilter.valueOf(performanceFilter) }.getOrDefault(PerformanceFilter.ALL),
    currentQuestionIndex = currentQuestionIndex,
    updatedAtEpochMillis = updatedAt,
    isLoggingEnabled = isLoggingEnabled,
    submissionMode = runCatching { SubmissionMode.valueOf(submissionMode) }.getOrDefault(SubmissionMode.INSTANT),
)
