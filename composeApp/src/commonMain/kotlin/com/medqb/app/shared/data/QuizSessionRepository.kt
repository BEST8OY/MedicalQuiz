package com.medqb.app.shared.data

import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.database.QuizSessionHistoryRow
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.platform.Logger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Manages quiz session history persistence via the active SQLite database.
 */
@Inject
class QuizSessionRepository(
    private val activeDatabaseHolder: ActiveDatabaseHolder,
) {
    private val _historyEntries = MutableStateFlow<List<QuizSession>>(emptyList())
    val historyEntries: StateFlow<List<QuizSession>> = _historyEntries.asStateFlow()

    private suspend fun db() = activeDatabaseHolder.databaseProvider.value
        ?: throw IllegalStateException("No active database")

    private suspend fun appendToHistory(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
        currentQuestionIndex: Int,
        isLoggingEnabled: Boolean = false,
        submissionMode: SubmissionMode = SubmissionMode.INSTANT,
        currentSessionId: String = "",
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

        db().upsertHistoryEntry(
            sessionId = sessionId,
            databaseName = databaseName,
            entryName = "",
            selectedSubjectIds = selectedSubjectIds.toSortedSet().toList(),
            selectedSystemIds = selectedSystemIds.toSortedSet().toList(),
            performanceFilter = performanceFilter.name,
            currentQuestionIndex = currentQuestionIndex,
            updatedAt = now,
            isLoggingEnabled = isLoggingEnabled,
            submissionMode = submissionMode.name,
        )

        return sessionId
    }

    suspend fun appendToHistoryAsync(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        selectedSystemIds: Set<Long>,
        performanceFilter: PerformanceFilter,
        currentQuestionIndex: Int,
        isLoggingEnabled: Boolean = false,
        submissionMode: SubmissionMode = SubmissionMode.INSTANT,
        currentSessionId: String = "",
    ): String = withContext(Dispatchers.IO) {
        val sessionId = appendToHistory(
            databaseName = databaseName,
            selectedSubjectIds = selectedSubjectIds,
            selectedSystemIds = selectedSystemIds,
            performanceFilter = performanceFilter,
            currentQuestionIndex = currentQuestionIndex,
            isLoggingEnabled = isLoggingEnabled,
            submissionMode = submissionMode,
            currentSessionId = currentSessionId,
        )
        _historyEntries.value = listHistory()
        sessionId
    }

    suspend fun listHistory(): List<QuizSession> {
        val provider = activeDatabaseHolder.databaseProvider.value ?: return emptyList()
        return withContext(Dispatchers.IO) {
            provider.listHistoryEntries().map { it.toQuizSession() }
        }
    }

    suspend fun refreshHistoryAsync(): List<QuizSession> = withContext(Dispatchers.IO) {
        val history = listHistory()
        _historyEntries.value = history
        history
    }

    suspend fun restoreHistoryEntry(entryId: String): QuizSession? = withContext(Dispatchers.IO) {
        db().getHistoryEntry(entryId)?.toQuizSession()
    }

    suspend fun restoreHistoryEntryAsync(entryId: String): QuizSession? = withContext(Dispatchers.IO) {
        restoreHistoryEntry(entryId).also {
            _historyEntries.value = listHistory()
        }
    }

    suspend fun deleteHistoryEntry(entryId: String) {
        deleteHistoryEntries(setOf(entryId))
    }

    suspend fun deleteHistoryEntries(entryIds: Set<String>) {
        if (entryIds.isEmpty()) return
        db().deleteHistoryEntries(entryIds.toList())
    }

    suspend fun deleteHistoryEntriesAsync(entryIds: Set<String>) = withContext(Dispatchers.IO) {
        deleteHistoryEntries(entryIds)
        _historyEntries.value = listHistory()
    }

    suspend fun deleteHistoryEntriesStrictAsync(entryIds: Set<String>) = withContext(Dispatchers.IO) {
        if (entryIds.isEmpty()) return@withContext
        db().deleteHistoryEntries(entryIds.toList())
        _historyEntries.value = listHistory()
    }

    suspend fun renameHistoryEntry(entryId: String, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return
        db().renameHistoryEntry(entryId, trimmedName)
    }

    suspend fun renameHistoryEntryAsync(entryId: String, newName: String) = withContext(Dispatchers.IO) {
        renameHistoryEntry(entryId, newName)
        _historyEntries.value = listHistory()
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

private fun QuizSessionHistoryRow.toQuizSession() = QuizSessionRepository.QuizSession(
    id = sessionId,
    databaseName = databaseName,
    entryName = entryName,
    selectedSubjectIds = selectedSubjectIds,
    selectedSystemIds = selectedSystemIds,
    performanceFilter = runCatching { PerformanceFilter.valueOf(performanceFilter) }.getOrDefault(PerformanceFilter.ALL),
    currentQuestionIndex = currentQuestionIndex,
    updatedAtEpochMillis = updatedAt,
    isLoggingEnabled = isLoggingEnabled,
    submissionMode = runCatching { SubmissionMode.valueOf(submissionMode) }.getOrDefault(SubmissionMode.INSTANT),
)
