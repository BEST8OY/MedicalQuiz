package com.medqb.app.shared.data.local.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.UserDatabaseConstructor
import com.medqb.app.shared.data.local.entity.MarkedQuestionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomMarkedQuestionDaoTest {

    private fun buildDb(): UserDatabase =
        Room.inMemoryDatabaseBuilder<UserDatabase> { UserDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun upsertAndIsMarkedWithDatabaseIsolation() = runTest {
        val db = buildDb()
        try {
            val dao = db.markedQuestionDao()

            assertFalse(dao.isMarked("bank", 1L))

            dao.upsert(MarkedQuestionEntity(dbName = "bank", questionId = 1L, markedAt = 1000L))

            assertTrue(dao.isMarked("bank", 1L))
            assertFalse(dao.isMarked("bank", 2L))
            // Isolation check: different dbName with same questionId
            assertFalse(dao.isMarked("other_bank", 1L))
        } finally {
            db.close()
        }
    }

    @Test
    fun getMarkedQuestionIdsReturnsSortedList() = runTest {
        val db = buildDb()
        try {
            val dao = db.markedQuestionDao()

            dao.upsert(MarkedQuestionEntity("bank", 30L, 100L))
            dao.upsert(MarkedQuestionEntity("bank", 10L, 200L))
            dao.upsert(MarkedQuestionEntity("bank", 20L, 300L))
            dao.upsert(MarkedQuestionEntity("other", 99L, 400L))

            val ids = dao.getMarkedQuestionIds("bank")
            assertEquals(listOf(10L, 20L, 30L), ids)
            assertEquals(3, dao.countMarked("bank"))
            assertEquals(1, dao.countMarked("other"))
        } finally {
            db.close()
        }
    }

    @Test
    fun observeMarkedQuestionIdsEmitsUpdates() = runTest {
        val db = buildDb()
        try {
            val dao = db.markedQuestionDao()

            assertEquals(emptyList(), dao.observeMarkedQuestionIds("bank").first())

            dao.upsert(MarkedQuestionEntity("bank", 5L, 100L))
            assertEquals(listOf(5L), dao.observeMarkedQuestionIds("bank").first())

            dao.upsert(MarkedQuestionEntity("bank", 12L, 200L))
            assertEquals(listOf(5L, 12L), dao.observeMarkedQuestionIds("bank").first())

            dao.delete("bank", 5L)
            assertEquals(listOf(12L), dao.observeMarkedQuestionIds("bank").first())
        } finally {
            db.close()
        }
    }

    @Test
    fun upsertReplacesRowOnConflictWithoutDuplicates() = runTest {
        val db = buildDb()
        try {
            val dao = db.markedQuestionDao()

            dao.upsert(MarkedQuestionEntity("bank", 1L, 1000L))
            dao.upsert(MarkedQuestionEntity("bank", 1L, 2000L))

            assertEquals(1, dao.countMarked("bank"))
            assertEquals(listOf(1L), dao.getMarkedQuestionIds("bank"))
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteAndClearForDatabaseRemovesRowsSafely() = runTest {
        val db = buildDb()
        try {
            val dao = db.markedQuestionDao()

            dao.upsert(MarkedQuestionEntity("bank1", 1L, 100L))
            dao.upsert(MarkedQuestionEntity("bank1", 2L, 200L))
            dao.upsert(MarkedQuestionEntity("bank2", 1L, 300L))

            dao.delete("bank1", 1L)
            assertEquals(listOf(2L), dao.getMarkedQuestionIds("bank1"))
            assertTrue(dao.isMarked("bank2", 1L))

            dao.clearForDatabase("bank1")
            assertEquals(emptyList(), dao.getMarkedQuestionIds("bank1"))
            assertEquals(listOf(1L), dao.getMarkedQuestionIds("bank2"))
        } finally {
            db.close()
        }
    }
}
