package com.medqb.app.shared.data.local.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.UserDatabaseConstructor
import com.medqb.app.shared.data.local.entity.LogEntity
import com.medqb.app.shared.data.local.entity.QuizSessionEntity
import com.medqb.app.shared.data.local.entity.SessionLogLinkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomSessionHistoryDaoTest {

    private fun buildDb(): UserDatabase =
        Room.inMemoryDatabaseBuilder<UserDatabase> { UserDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun upsertHistoryCreatesAndUpdatesPreservingBlankName() = runTest {
        val db = buildDb()
        try {
            val dao = db.sessionHistoryDao()

            dao.upsertHistory(
                sessionId = "s1",
                databaseName = "cardiology",
                entryName = "Cardio Quiz 1",
                selectedSubjectIds = "1,2",
                selectedSystemIds = "3",
                performanceFilter = "ALL",
                currentQuestionIndex = 0,
                updatedAt = 1000L,
                isLoggingEnabled = true,
                submissionMode = "INSTANT",
                selectedDifficultyTiers = "EASY,DIFFICULT",
            )

            val created = dao.getHistory("s1")
            assertNotNull(created)
            assertEquals("Cardio Quiz 1", created.entryName)
            assertEquals("cardiology", created.databaseName)
            assertEquals(1000L, created.updatedAt)

            // Update with blank entryName -> must preserve existing name
            dao.upsertHistory(
                sessionId = "s1",
                databaseName = "cardiology",
                entryName = "",
                selectedSubjectIds = "1,2",
                selectedSystemIds = "3",
                performanceFilter = "ALL",
                currentQuestionIndex = 5,
                updatedAt = 2000L,
                isLoggingEnabled = true,
                submissionMode = "INSTANT",
                selectedDifficultyTiers = "EASY,DIFFICULT",
            )

            val preserved = dao.getHistory("s1")
            assertNotNull(preserved)
            assertEquals("Cardio Quiz 1", preserved.entryName)
            assertEquals(5, preserved.currentQuestionIndex)
            assertEquals(2000L, preserved.updatedAt)

            // Explicit rename
            dao.renameHistory("s1", "Updated Name")
            val renamed = dao.getHistory("s1")
            assertNotNull(renamed)
            assertEquals("Updated Name", renamed.entryName)
        } finally {
            db.close()
        }
    }

    @Test
    fun listHistoryOrdersByUpdatedAtDesc() = runTest {
        val db = buildDb()
        try {
            val dao = db.sessionHistoryDao()

            dao.upsertHistory("s1", "db", "First", "", "", "ALL", 0, 100L, true, "INSTANT", "")
            dao.upsertHistory("s2", "db", "Second", "", "", "ALL", 0, 300L, true, "INSTANT", "")
            dao.upsertHistory("s3", "db", "Third", "", "", "ALL", 0, 200L, true, "INSTANT", "")

            val list = dao.listHistory().first()
            assertEquals(listOf("s2", "s3", "s1"), list.map { it.sessionId })
        } finally {
            db.close()
        }
    }

    @Test
    fun foreignKeyCascadeDeletesLinksWhenLogIsDeleted() = runTest {
        val db = buildDb()
        try {
            val logDao = db.logDao()
            val sessionDao = db.sessionHistoryDao()

            val logId = logDao.insert(
                LogEntity(
                    dbName = "db",
                    qid = 42L,
                    selectedAnswer = 1,
                    corrAnswer = 1,
                    time = 50L,
                    answerDate = "2026-01-01 00:00:00"
                )
            )

            sessionDao.ensureSessionExists(QuizSessionEntity("sess-1"))
            sessionDao.insertLogLink(SessionLogLinkEntity("sess-1", logId))

            // Delete the log via logDao
            logDao.clearForQuestion("db", 42L)

            // Orphan cleanup removes sessions if no links and no history
            sessionDao.deleteOrphanedSessions()

            // Since the link was cascade deleted by SQLite, the session now has 0 links
            // and should be deleted as an orphan
            val history = sessionDao.getHistory("sess-1")
            assertNull(history)
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteHistoryRemovesSpecifiedSessions() = runTest {
        val db = buildDb()
        try {
            val dao = db.sessionHistoryDao()

            dao.upsertHistory("s1", "db", "First", "", "", "ALL", 0, 100L, true, "INSTANT", "")
            dao.upsertHistory("s2", "db", "Second", "", "", "ALL", 0, 200L, true, "INSTANT", "")

            dao.deleteHistory(listOf("s1"))

            val list = dao.listHistoryOnce()
            assertEquals(1, list.size)
            assertEquals("s2", list[0].sessionId)
        } finally {
            db.close()
        }
    }
}
