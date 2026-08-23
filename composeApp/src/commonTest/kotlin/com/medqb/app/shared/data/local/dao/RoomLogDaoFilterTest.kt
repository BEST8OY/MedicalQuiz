package com.medqb.app.shared.data.local.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.UserDatabaseConstructor
import com.medqb.app.shared.data.local.entity.LogEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the per-filter SQL in [RoomLogDao] against a real in-memory database —
 * these queries back PerformanceFilter selection (see QuestionDao).
 */
class RoomLogDaoFilterTest {

    private fun buildDb(): UserDatabase =
        Room.inMemoryDatabaseBuilder<UserDatabase> { UserDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()

    private fun log(dbName: String, qid: Long, correct: Boolean) = LogEntity(
        dbName = dbName,
        qid = qid,
        selectedAnswer = 1,
        corrAnswer = if (correct) 1 else 2,
        time = 100L,
        answerDate = "2026-01-01 00:00:00"
    )

    @Test
    fun lastAttemptFiltersReflectOnlyMostRecentRow() = runTest {
        val db = buildDb()
        try {
            val dao = db.logDao()
            // q1: answered wrong first, then correct -> LAST_CORRECT
            dao.insert(log("bank", 1, correct = false))
            dao.insert(log("bank", 1, correct = true))
            // q2: correct first, then wrong -> LAST_INCORRECT
            dao.insert(log("bank", 2, correct = true))
            dao.insert(log("bank", 2, correct = false))
            // q3: single correct attempt
            dao.insert(log("bank", 3, correct = true))

            assertEquals(setOf(1L, 3L), dao.getLastCorrectQids("bank").toSet())
            assertEquals(setOf(2L), dao.getLastIncorrectQids("bank").toSet())
        } finally {
            db.close()
        }
    }

    @Test
    fun everFiltersAggregateAllAttempts() = runTest {
        val db = buildDb()
        try {
            val dao = db.logDao()
            // q1 has both outcomes across attempts
            dao.insert(log("bank", 1, correct = true))
            dao.insert(log("bank", 1, correct = false))
            // q2 only ever incorrect
            dao.insert(log("bank", 2, correct = false))

            assertEquals(setOf(1L), dao.getEverCorrectQids("bank").toSet())
            assertEquals(setOf(1L, 2L), dao.getEverIncorrectQids("bank").toSet())
        } finally {
            db.close()
        }
    }

    @Test
    fun unloggedQuestionsAppearNowhereAndLogsAreIsolatedPerDb() = runTest {
        val db = buildDb()
        try {
            val dao = db.logDao()
            dao.insert(log("bank-a", 10, correct = true))
            dao.insert(log("bank-b", 20, correct = false))

            assertEquals(listOf(10L), dao.getAllLoggedQids("bank-a"))
            assertEquals(setOf(10L), dao.getLastCorrectQids("bank-a").toSet())
            assertEquals(emptyList(), dao.getLastCorrectQids("bank-b"))
            assertEquals(setOf(20L), dao.getLastIncorrectQids("bank-b").toSet())
            assertEquals(emptySet(), dao.getEverCorrectQids("bank-b").toSet())
        } finally {
            db.close()
        }
    }

    @Test
    fun clearingQuestionRemovesItsRowsForThatDbOnly() = runTest {
        val db = buildDb()
        try {
            val dao = db.logDao()
            dao.insert(log("bank-a", 10, correct = true))
            dao.insert(log("bank-b", 10, correct = true))
            val rowids = dao.getLogRowIds("bank-a", 10)
            assertEquals(1, rowids.size)

            dao.clearForQuestion("bank-a", 10)

            assertEquals(emptyList(), dao.getLogRowIds("bank-a", 10))
            assertEquals(1, dao.getLogRowIds("bank-b", 10).size)
        } finally {
            db.close()
        }
    }
}
