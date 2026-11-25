package com.medqb.app.shared.data

import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.models.SubmissionMode
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Quiz session history persistence (upsert/rename/delete/restore) with a reactive
 * [historyEntries] feed.
 */
interface QuizSessionRepository {
    val historyEntries: StateFlow<List<QuizSession>>

    fun generateSessionId(databaseName: String): String {
        val canonicalDbName = canonicalizeDbName(databaseName)
        val now = Clock.System.now().toEpochMilliseconds()
        return "$canonicalDbName-$now-${Random.nextLong().toULong().toString(16)}"
    }

    suspend fun listHistory(): List<QuizSession>

    suspend fun appendToHistory(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        excludedSubjectIds: Set<Long> = emptySet(),
        selectedSystemIds: Set<Long>,
        excludedSystemIds: Set<Long> = emptySet(),
        statusFilter: StatusFilter,
        currentQuestionIndex: Int,
        currentQuestionId: Long? = null,
        isLoggingEnabled: Boolean = false,
        submissionMode: SubmissionMode = SubmissionMode.INSTANT,
        currentSessionId: String = "",
        entryName: String = "",
        selectedDifficultyTiers: Set<DifficultyTier> = emptySet(),
    ): String

    /**
     * Enqueues a non-blocking, coalesced history write onto the repository's
     * process-scoped actor so rapid navigation or leaving the quiz screen cannot
     * cancel or drop pending updates.
     */
    fun enqueueHistoryUpdate(
        databaseName: String,
        selectedSubjectIds: Set<Long>,
        excludedSubjectIds: Set<Long> = emptySet(),
        selectedSystemIds: Set<Long>,
        excludedSystemIds: Set<Long> = emptySet(),
        statusFilter: StatusFilter,
        currentQuestionIndex: Int,
        currentQuestionId: Long? = null,
        isLoggingEnabled: Boolean = false,
        submissionMode: SubmissionMode = SubmissionMode.INSTANT,
        currentSessionId: String = "",
        entryName: String = "",
        selectedDifficultyTiers: Set<DifficultyTier> = emptySet(),
    )

    suspend fun deleteHistoryEntries(entryIds: Set<String>)

    suspend fun renameHistoryEntry(entryId: String, newName: String)

    suspend fun restoreDeletedHistoryEntry(entry: QuizSession)

    suspend fun restoreHistoryEntry(entryId: String): QuizSession?

    data class QuizSession(
        val id: String = "",
        val databaseName: String,
        val entryName: String = "",
        val selectedSubjectIds: List<Long>,
        val excludedSubjectIds: List<Long> = emptyList(),
        val selectedSystemIds: List<Long>,
        val excludedSystemIds: List<Long> = emptyList(),
        val statusFilter: StatusFilter,
        val currentQuestionIndex: Int,
        val currentQuestionId: Long? = null,
        val updatedAtEpochMillis: Long = 0,
        val isLoggingEnabled: Boolean = false,
        val submissionMode: SubmissionMode = SubmissionMode.INSTANT,
        val selectedDifficultyTiers: Set<DifficultyTier> = emptySet(),
    )
}
