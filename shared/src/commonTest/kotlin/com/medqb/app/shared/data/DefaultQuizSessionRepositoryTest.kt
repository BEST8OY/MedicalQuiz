package com.medqb.app.shared.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.UserDatabaseConstructor
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.viewmodel.FakeUserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
                statusFilter = StatusFilter.ALL,
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
            assertEquals("cardiology", session.databaseName)
            assertEquals("Cardio Quiz 1", session.entryName)
            assertEquals(listOf(1L, 2L), session.selectedSubjectIds)
            assertEquals(listOf(5L), session.excludedSubjectIds)
            assertEquals(listOf(10L), session.selectedSystemIds)
            assertEquals(listOf(20L), session.excludedSystemIds)
            assertEquals(3, session.currentQuestionIndex)
            assertEquals(StatusFilter.ALL, session.statusFilter)
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
                statusFilter = StatusFilter.ALL,
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
                statusFilter = StatusFilter.ALL,
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
                statusFilter = StatusFilter.ALL,
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
                statusFilter = StatusFilter.ALL,
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

    @Test
    fun canonicalizesDbNameCaseInsensitivelyAndPersistsCurrentQuestionId() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultQuizSessionRepository(FakeUserDataManager(db))
            val sessionId = repository.appendToHistory(
                databaseName = "  Cardiology.DB  ",
                selectedSubjectIds = setOf(3L, 1L),
                selectedSystemIds = emptySet(),
                statusFilter = StatusFilter.ALL,
                currentQuestionIndex = 2,
                currentQuestionId = 99L,
                currentSessionId = "sess-canon",
            )

            assertEquals("sess-canon", sessionId)
            val restored = repository.restoreHistoryEntry("sess-canon")
            assertNotNull(restored)
            assertEquals("Cardiology", restored.databaseName)
            assertEquals(2, restored.currentQuestionIndex)
            assertEquals(99L, restored.currentQuestionId)
        } finally {
            db.close()
        }
    }

    @Test
    fun restoreDeletedHistoryEntryValidatesAndSortsIds() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultQuizSessionRepository(FakeUserDataManager(db))

            repository.restoreDeletedHistoryEntry(
                QuizSessionRepository.QuizSession(
                    id = "",
                    databaseName = "cardio",
                    selectedSubjectIds = listOf(1L),
                    selectedSystemIds = emptyList(),
                    statusFilter = StatusFilter.ALL,
                    currentQuestionIndex = 0,
                )
            )
            repository.restoreDeletedHistoryEntry(
                QuizSessionRepository.QuizSession(
                    id = "s-blank-db",
                    databaseName = "   ",
                    selectedSubjectIds = listOf(1L),
                    selectedSystemIds = emptyList(),
                    statusFilter = StatusFilter.ALL,
                    currentQuestionIndex = 0,
                )
            )
            assertTrue(repository.listHistory().isEmpty())

            repository.restoreDeletedHistoryEntry(
                QuizSessionRepository.QuizSession(
                    id = "s-valid",
                    databaseName = "  Cardio.DB ",
                    selectedSubjectIds = listOf(5L, 2L, 5L),
                    excludedSubjectIds = listOf(9L, 3L),
                    selectedSystemIds = listOf(8L, 1L),
                    excludedSystemIds = listOf(4L, 2L, 4L),
                    statusFilter = StatusFilter.ALL,
                    currentQuestionIndex = 4,
                    currentQuestionId = 77L,
                )
            )
            val restored = repository.restoreHistoryEntry("s-valid")
            assertNotNull(restored)
            assertEquals("Cardio", restored.databaseName)
            assertEquals(listOf(2L, 5L), restored.selectedSubjectIds)
            assertEquals(listOf(3L, 9L), restored.excludedSubjectIds)
            assertEquals(listOf(1L, 8L), restored.selectedSystemIds)
            assertEquals(listOf(2L, 4L), restored.excludedSystemIds)
            assertEquals(77L, restored.currentQuestionId)
        } finally {
            db.close()
        }
    }

    @Test
    fun historyEntriesDoesNotReopenOnCloseAndRecoversOnReopen() = runTest {
        val db1 = buildDb()
        val db2 = buildDb()
        var currentDb: UserDatabase? = null
        var openCount = 0
        var closedExplicitly = false
        val manager = object : UserDataManager() {
            private fun ensureOpen(): UserDatabase {
                currentDb?.let { return it }
                val next = if (openCount == 0) db1 else db2
                openCount++
                currentDb = next
                if (closedExplicitly) {
                    closedExplicitly = false
                    setDbState(DbState.Open(nextOpenEpoch()))
                }
                return next
            }

            override suspend fun init() {
                ensureOpen()
            }

            override suspend fun logDao() = ensureOpen().logDao()
            override suspend fun sessionHistoryDao() = ensureOpen().sessionHistoryDao()
            override suspend fun textHighlightDao() = ensureOpen().textHighlightDao()
            override suspend fun markedQuestionDao() = ensureOpen().markedQuestionDao()
            override suspend fun <R> withTransaction(block: suspend () -> R): R = block()

            override suspend fun close() {
                currentDb?.close()
                currentDb = null
                closedExplicitly = true
                setDbState(DbState.Closed)
            }
        }

        try {
            val repository = DefaultQuizSessionRepository(manager)
            withContext(Dispatchers.Default) {
                repository.appendToHistory(
                    databaseName = "bank",
                    selectedSubjectIds = emptySet(),
                    selectedSystemIds = emptySet(),
                    statusFilter = StatusFilter.ALL,
                    currentQuestionIndex = 0,
                    currentSessionId = "s1",
                )
                withTimeout(5_000) {
                    repository.historyEntries.first { list -> list.any { it.id == "s1" } }
                }
                assertEquals(1, openCount, "Initial subscribe + write must open DB exactly once")

                manager.close()
                withTimeout(5_000) {
                    repository.historyEntries.first { it.isEmpty() }
                }
                assertEquals(1, openCount, "close() must not trigger a lazy reopen")

                manager.init()
                repository.appendToHistory(
                    databaseName = "bank",
                    selectedSubjectIds = emptySet(),
                    selectedSystemIds = emptySet(),
                    statusFilter = StatusFilter.ALL,
                    currentQuestionIndex = 0,
                    currentQuestionId = 10L,
                    currentSessionId = "after-reopen",
                )
                withTimeout(5_000) {
                    repository.historyEntries.first { list -> list.any { it.id == "after-reopen" } }
                }
                assertEquals(2, openCount)
            }
        } finally {
            db1.close()
            db2.close()
        }
    }

    @Test
    fun enqueueHistoryUpdateCoalescesAndFlushesOnRepositoryScope() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultQuizSessionRepository(FakeUserDataManager(db))
            repository.enqueueHistoryUpdate(
                databaseName = "cardiology",
                selectedSubjectIds = setOf(1L),
                selectedSystemIds = emptySet(),
                statusFilter = StatusFilter.ALL,
                currentQuestionIndex = 0,
                currentQuestionId = 10L,
                currentSessionId = "sess-queued",
            )
            repository.enqueueHistoryUpdate(
                databaseName = "cardiology",
                selectedSubjectIds = setOf(1L),
                selectedSystemIds = emptySet(),
                statusFilter = StatusFilter.ALL,
                currentQuestionIndex = 4,
                currentQuestionId = 50L,
                currentSessionId = "sess-queued",
            )
            withContext(Dispatchers.Default) {
                repository.flushPendingHistoryWrites()
            }

            val restored = repository.restoreHistoryEntry("sess-queued")
            assertNotNull(restored)
            assertEquals(4, restored.currentQuestionIndex)
            assertEquals(50L, restored.currentQuestionId)
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteAndRenameFlushPendingEnqueuedUpdatesWithoutResurrection() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultQuizSessionRepository(FakeUserDataManager(db))
            repository.enqueueHistoryUpdate(
                databaseName = "cardiology",
                selectedSubjectIds = setOf(1L),
                selectedSystemIds = emptySet(),
                statusFilter = StatusFilter.ALL,
                currentQuestionIndex = 1,
                currentQuestionId = 20L,
                currentSessionId = "sess-del",
                entryName = "To Delete",
            )
            repository.enqueueHistoryUpdate(
                databaseName = "cardiology",
                selectedSubjectIds = setOf(1L),
                selectedSystemIds = emptySet(),
                statusFilter = StatusFilter.ALL,
                currentQuestionIndex = 2,
                currentQuestionId = 30L,
                currentSessionId = "sess-rename",
                entryName = "Old Name",
            )

            withContext(Dispatchers.Default) {
                repository.deleteHistoryEntries(setOf("sess-del"))
                repository.renameHistoryEntry("sess-rename", "Final Name")
                repository.flushPendingHistoryWrites()
            }

            assertNull(repository.restoreHistoryEntry("sess-del"))
            val renamed = repository.restoreHistoryEntry("sess-rename")
            assertNotNull(renamed)
            assertEquals("Final Name", renamed.entryName)
            assertEquals(30L, renamed.currentQuestionId)
        } finally {
            db.close()
        }
    }

    @Test
    fun logAnswerPopulatesSessionCreatedAt() = runTest {
        val db = buildDb()
        try {
            val logDao = com.medqb.app.shared.data.dao.LogDao(FakeUserDataManager(db))
            logDao.logAnswer(
                dbName = "  Cardio.DB ",
                qid = 10L,
                selectedAnswer = 1,
                corrAnswer = 1,
                time = 5L,
                sessionId = "sess-created-at",
            )
            val session = db.sessionHistoryDao().getSession("sess-created-at")
            assertNotNull(session)
            assertTrue(session.createdAt.isNotBlank(), "QuizSessionEntity.createdAt should be populated")
            assertEquals(1, db.sessionHistoryDao().countLinksForSession("sess-created-at"))
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteHistoryEntriesChunksMoreThanSqliteVariableLimit() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultQuizSessionRepository(FakeUserDataManager(db))
            val ids = (1..1200).map { "sess-$it" }.toSet()
            ids.forEach { id ->
                repository.appendToHistory(
                    databaseName = "cardiology",
                    selectedSubjectIds = emptySet(),
                    selectedSystemIds = emptySet(),
                    statusFilter = StatusFilter.ALL,
                    currentQuestionIndex = 0,
                    currentSessionId = id,
                )
            }
            assertEquals(1200, repository.listHistory().size)

            repository.deleteHistoryEntries(ids)
            assertTrue(repository.listHistory().isEmpty())
        } finally {
            db.close()
        }
    }
}
