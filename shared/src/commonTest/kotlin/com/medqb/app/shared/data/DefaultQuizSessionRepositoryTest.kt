package com.medqb.app.shared.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.UserDatabaseConstructor
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.viewmodel.FakeUserDataManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultQuizSessionRepositoryTest {

    private fun buildDb(): UserDatabase =
        Room.inMemoryDatabaseBuilder<UserDatabase> { UserDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun appendAndListHistory() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultQuizSessionRepository(FakeUserDataManager(db))

            val sessionId = repository.appendToHistory(
                databaseName = "cardiology.db",
                selectedSubjectIds = setOf(1L, 2L),
                excludedSubjectIds = setOf(5L),
                selectedSystemIds = setOf(10L),
                excludedSystemIds = setOf(20L),
                performanceFilter = PerformanceFilter.ALL,
                currentQuestionIndex = 3,
                isLoggingEnabled = true,
                submissionMode = SubmissionMode.INSTANT,
                currentSessionId = "sess-123",
                entryName = "Cardio Quiz 1",
                selectedDifficultyTiers = setOf(DifficultyTier.EASY, DifficultyTier.DIFFICULT),
            )

            assertEquals("sess-123", sessionId)

            val history = repository.listHistory()
            assertEquals(1, history.size)
            val session = history.first()
            assertEquals("sess-123", session.id)
            assertEquals("cardiology.db", session.databaseName)
            assertEquals("Cardio Quiz 1", session.entryName)
            assertEquals(listOf(1L, 2L), session.selectedSubjectIds)
            assertEquals(listOf(5L), session.excludedSubjectIds)
            assertEquals(listOf(10L), session.selectedSystemIds)
            assertEquals(listOf(20L), session.excludedSystemIds)
            assertEquals(3, session.currentQuestionIndex)
            assertEquals(PerformanceFilter.ALL, session.performanceFilter)
            assertEquals(SubmissionMode.INSTANT, session.submissionMode)
            assertEquals(setOf(DifficultyTier.DIFFICULT, DifficultyTier.EASY), session.selectedDifficultyTiers)
        } finally {
            db.close()
        }
    }

    @Test
    fun appendToHistoryWithBlankDatabaseNameReturnsEmptyString() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultQuizSessionRepository(FakeUserDataManager(db))

            val result = repository.appendToHistory(
                databaseName = "",
                selectedSubjectIds = emptySet(),
                excludedSubjectIds = emptySet(),
                selectedSystemIds = emptySet(),
                excludedSystemIds = emptySet(),
                performanceFilter = PerformanceFilter.ALL,
                currentQuestionIndex = 0,
                isLoggingEnabled = true,
                submissionMode = SubmissionMode.INSTANT,
                currentSessionId = "",
                entryName = "",
                selectedDifficultyTiers = emptySet(),
            )

            assertEquals("", result)
            assertTrue(repository.listHistory().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun renameHistoryEntryUpdatesName() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultQuizSessionRepository(FakeUserDataManager(db))

            repository.appendToHistory(
                databaseName = "cardiology.db",
                selectedSubjectIds = emptySet(),
                excludedSubjectIds = emptySet(),
                selectedSystemIds = emptySet(),
                excludedSystemIds = emptySet(),
                performanceFilter = PerformanceFilter.ALL,
                currentQuestionIndex = 0,
                isLoggingEnabled = true,
                submissionMode = SubmissionMode.INSTANT,
                currentSessionId = "sess-1",
                entryName = "Old Name",
                selectedDifficultyTiers = emptySet(),
            )

            repository.renameHistoryEntry("sess-1", "New Name")

            val restored = repository.restoreHistoryEntry("sess-1")
            assertNotNull(restored)
            assertEquals("New Name", restored.entryName)
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteHistoryEntriesRemovesSpecifiedSessions() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultQuizSessionRepository(FakeUserDataManager(db))

            repository.appendToHistory(
                databaseName = "cardiology.db",
                selectedSubjectIds = emptySet(),
                excludedSubjectIds = emptySet(),
                selectedSystemIds = emptySet(),
                excludedSystemIds = emptySet(),
                performanceFilter = PerformanceFilter.ALL,
                currentQuestionIndex = 0,
                isLoggingEnabled = true,
                submissionMode = SubmissionMode.INSTANT,
                currentSessionId = "s1",
                entryName = "Quiz 1",
                selectedDifficultyTiers = emptySet(),
            )
            repository.appendToHistory(
                databaseName = "cardiology.db",
                selectedSubjectIds = emptySet(),
                excludedSubjectIds = emptySet(),
                selectedSystemIds = emptySet(),
                excludedSystemIds = emptySet(),
                performanceFilter = PerformanceFilter.ALL,
                currentQuestionIndex = 0,
                isLoggingEnabled = true,
                submissionMode = SubmissionMode.INSTANT,
                currentSessionId = "s2",
                entryName = "Quiz 2",
                selectedDifficultyTiers = emptySet(),
            )

            assertEquals(2, repository.listHistory().size)

            repository.deleteHistoryEntries(setOf("s1"))

            val remaining = repository.listHistory()
            assertEquals(1, remaining.size)
            assertEquals("s2", remaining.first().id)
            assertNull(repository.restoreHistoryEntry("s1"))
        } finally {
            db.close()
        }
    }
}
