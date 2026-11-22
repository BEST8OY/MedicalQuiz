package com.medqb.app.shared.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.UserDatabaseConstructor
import com.medqb.app.shared.viewmodel.FakeUserDataManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultMarkedQuestionsRepositoryTest {

    private fun buildDb(): UserDatabase =
        Room.inMemoryDatabaseBuilder<UserDatabase> { UserDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun canonicalizesDatabaseNameConsistently() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultMarkedQuestionsRepository(FakeUserDataManager(db))

            repository.setMarked("USMLE_Step1.db", 42L, true)

            // Should be accessible via "USMLE_Step1", "USMLE_Step1.db", or with spaces
            assertTrue(repository.isMarked("USMLE_Step1", 42L))
            assertTrue(repository.isMarked("USMLE_Step1.db", 42L))
            assertTrue(repository.isMarked("  USMLE_Step1.db  ", 42L))

            val ids = repository.getMarkedQuestionIds("USMLE_Step1")
            assertEquals(setOf(42L), ids)
        } finally {
            db.close()
        }
    }

    @Test
    fun toggleMarkTogglesBetweenTrueAndFalse() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultMarkedQuestionsRepository(FakeUserDataManager(db))

            assertFalse(repository.isMarked("bank", 1L))

            // First toggle: unmarked -> marked
            val result1 = repository.toggleMark("bank", 1L)
            assertTrue(result1)
            assertTrue(repository.isMarked("bank", 1L))
            assertEquals(setOf(1L), repository.getMarkedQuestionIds("bank"))

            // Second toggle: marked -> unmarked
            val result2 = repository.toggleMark("bank", 1L)
            assertFalse(result2)
            assertFalse(repository.isMarked("bank", 1L))
            assertTrue(repository.getMarkedQuestionIds("bank").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun setMarkedIsIdempotent() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultMarkedQuestionsRepository(FakeUserDataManager(db))

            assertTrue(repository.setMarked("bank", 10L, true))
            assertTrue(repository.setMarked("bank", 10L, true))
            assertEquals(setOf(10L), repository.getMarkedQuestionIds("bank"))

            assertFalse(repository.setMarked("bank", 10L, false))
            assertFalse(repository.setMarked("bank", 10L, false))
            assertTrue(repository.getMarkedQuestionIds("bank").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun observeMarkedQuestionIdsEmitsSetsReactively() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultMarkedQuestionsRepository(FakeUserDataManager(db))

            assertEquals(emptySet(), repository.observeMarkedQuestionIds("bank").first())

            repository.setMarked("bank", 100L, true)
            assertEquals(setOf(100L), repository.observeMarkedQuestionIds("bank").first())

            repository.setMarked("bank", 200L, true)
            assertEquals(setOf(100L, 200L), repository.observeMarkedQuestionIds("bank").first())

            repository.setMarked("bank", 100L, false)
            assertEquals(setOf(200L), repository.observeMarkedQuestionIds("bank").first())
        } finally {
            db.close()
        }
    }

    @Test
    fun clearForDatabaseIsolatesDatabases() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultMarkedQuestionsRepository(FakeUserDataManager(db))

            repository.setMarked("bankA", 1L, true)
            repository.setMarked("bankB", 2L, true)

            repository.clearForDatabase("bankA")

            assertTrue(repository.getMarkedQuestionIds("bankA").isEmpty())
            assertEquals(setOf(2L), repository.getMarkedQuestionIds("bankB"))
        } finally {
            db.close()
        }
    }
}
