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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Manages quiz session history persistence directly via [SessionHistoryManager].
 *
 * Uses Room's reactive Flow to automatically keep [historyEntries] in sync.
 */
@Inject
@SingleIn(AppScope::class)
class QuizSessionRepository(
    private val sessionHistoryManager: SessionHistoryManager,
) {
    // Process-scoped: intentionally not cancelled — survives config changes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val historyEntries: StateFlow<List<QuizSession>> = sessionHistoryManager.sessionHistoryDao()
        .listHistory()
        .map { entities -> entities.map { it.toQuizSession() } }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    suspend fun listHistory(): List<QuizSession> = withContext(Dispatchers.IO) {
        sessionHistoryManager.sessionHistoryDao().listHistoryOnce().map { it.toQuizSession() }
    }

    suspend fun appendToHistory(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
        currentQuestionIndex: Int,
        isLoggingEnabled: Boolean = false,
        submissionMode: SubmissionMode = SubmissionMode.INSTANT,
        currentSessionId: String = "",
        entryName: String = "",
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
            sessionHistoryManager.sessionHistoryDao().upsertHistory(
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

    suspend fun deleteHistoryEntries(entryIds: Set<String>) = withContext(Dispatchers.IO) {
        if (entryIds.isEmpty()) return@withContext
        val dao = sessionHistoryManager.sessionHistoryDao()
        dao.deleteHistory(entryIds.toList())
        dao.deleteOrphanedSessions()
    }

    suspend fun renameHistoryEntry(entryId: String, newName: String) = withContext(Dispatchers.IO) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return@withContext
        sessionHistoryManager.sessionHistoryDao().renameHistory(entryId, trimmedName)
    }

    suspend fun restoreDeletedHistoryEntry(entry: QuizSession) = withContext(Dispatchers.IO) {
        sessionHistoryManager.sessionHistoryDao().upsertHistory(
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

    suspend fun restoreHistoryEntry(entryId: String): QuizSession? = withContext(Dispatchers.IO) {
        sessionHistoryManager.sessionHistoryDao().getHistory(entryId)?.toQuizSession()
    }

    private fun buildSessionId(databaseName: String, now: Long): String = "$databaseName-$now"

    data class QuizSession(
        val id: String = "",
        val databaseName: String,
        val entryName: String = "",
        val selectedSubjectIds: List<Long>,
        val selectedSystemIds: List<Long>,
        val performanceFilter: PerformanceFilter,
        val currentQuestionIndex: Int,
        val updatedAtEpochMillis: Long = 0,
        val isLoggingEnabled: Boolean = false,
        val submissionMode: SubmissionMode = SubmissionMode.INSTANT,
    )
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
