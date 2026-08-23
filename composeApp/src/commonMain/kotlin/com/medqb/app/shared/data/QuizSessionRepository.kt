package com.medqb.app.shared.data

import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.models.SubmissionMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Quiz session history persistence (upsert/rename/delete/restore) with a reactive
 * [historyEntries] feed.
 */
interface QuizSessionRepository {
    val historyEntries: StateFlow<List<QuizSession>>

    suspend fun listHistory(): List<QuizSession>

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
    ): String

    suspend fun deleteHistoryEntries(entryIds: Set<String>)

    suspend fun renameHistoryEntry(entryId: String, newName: String)

    suspend fun restoreDeletedHistoryEntry(entry: QuizSession)

    suspend fun restoreHistoryEntry(entryId: String): QuizSession?

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
