package com.medqb.app.shared.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.UserDatabaseConstructor
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.viewmodel.FakeUserDataManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultTextHighlightsRepositoryTest {

    private fun buildDb(): UserDatabase =
        Room.inMemoryDatabaseBuilder<UserDatabase> { UserDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun addAndRetrieveHighlights() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultTextHighlightsRepository(FakeUserDataManager(db))

            val highlights = repository.addHighlight(
                dbName = "bank.db",
                questionId = 1L,
                section = HighlightSection.QUESTION,
                startOffset = 5,
                endOffset = 16,
                highlightedText = "my question",
                color = HighlightColor.YELLOW,
            )

            assertEquals(1, highlights.size)
            val first = highlights.first()
            assertEquals("bank.db", first.dbName)
            assertEquals(1L, first.questionId)
            assertEquals(HighlightSection.QUESTION, first.section)
            assertEquals(5, first.startOffset)
            assertEquals(16, first.endOffset)
            assertEquals("my question", first.highlightedText)
            assertEquals(HighlightColor.YELLOW, first.color)
        } finally {
            db.close()
        }
    }

    @Test
    fun addIdenticalHighlightIsNoOp() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultTextHighlightsRepository(FakeUserDataManager(db))

            repository.addHighlight(
                dbName = "bank.db",
                questionId = 1L,
                section = HighlightSection.QUESTION,
                startOffset = 0,
                endOffset = 10,
                highlightedText = "Diagnostic",
                color = HighlightColor.GREEN,
            )

            val second = repository.addHighlight(
                dbName = "bank.db",
                questionId = 1L,
                section = HighlightSection.QUESTION,
                startOffset = 0,
                endOffset = 10,
                highlightedText = "Diagnostic",
                color = HighlightColor.GREEN,
            )

            assertEquals(1, second.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun overlappingHighlightsMergeTogether() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultTextHighlightsRepository(FakeUserDataManager(db))

            // First highlight [0, 5] = "Hello"
            repository.addHighlight(
                dbName = "bank.db",
                questionId = 1L,
                section = HighlightSection.QUESTION,
                startOffset = 0,
                endOffset = 5,
                highlightedText = "Hello",
                color = HighlightColor.YELLOW,
            )

            // Overlapping highlight [3, 11] = "lo World"
            val merged = repository.addHighlight(
                dbName = "bank.db",
                questionId = 1L,
                section = HighlightSection.QUESTION,
                startOffset = 3,
                endOffset = 11,
                highlightedText = "lo World",
                color = HighlightColor.YELLOW,
            )

            // Must merge into a single highlight [0, 11] = "Hello World"
            assertEquals(1, merged.size)
            val highlight = merged.first()
            assertEquals(0, highlight.startOffset)
            assertEquals(11, highlight.endOffset)
            assertEquals("Hello World", highlight.highlightedText)
        } finally {
            db.close()
        }
    }

    @Test
    fun updateHighlightColorPersistsChange() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultTextHighlightsRepository(FakeUserDataManager(db))

            val initial = repository.addHighlight(
                dbName = "bank.db",
                questionId = 1L,
                section = HighlightSection.QUESTION,
                startOffset = 0,
                endOffset = 4,
                highlightedText = "Test",
                color = HighlightColor.YELLOW,
            )
            val id = initial.first().id

            val updated = repository.updateHighlightColor(
                dbName = "bank.db",
                questionId = 1L,
                highlightId = id,
                color = HighlightColor.PINK,
            )

            assertEquals(1, updated.size)
            assertEquals(HighlightColor.PINK, updated.first().color)
        } finally {
            db.close()
        }
    }

    @Test
    fun removeHighlightDeletesAndReturnsRemaining() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultTextHighlightsRepository(FakeUserDataManager(db))

            val first = repository.addHighlight("bank.db", 1L, HighlightSection.QUESTION, 0, 4, "One ", HighlightColor.YELLOW)
            val second = repository.addHighlight("bank.db", 1L, HighlightSection.QUESTION, 10, 14, "Two ", HighlightColor.GREEN)
            assertEquals(2, second.size)

            val remaining = repository.removeHighlight("bank.db", 1L, first.first().id)
            assertEquals(1, remaining.size)
            assertEquals(10, remaining.first().startOffset)
        } finally {
            db.close()
        }
    }

    @Test
    fun emptyDbNameReturnsEmptyList() = runTest {
        val db = buildDb()
        try {
            val repository = DefaultTextHighlightsRepository(FakeUserDataManager(db))

            val list = repository.getHighlightsForQuestion("", 1L)
            assertTrue(list.isEmpty())

            val added = repository.addHighlight("", 1L, HighlightSection.QUESTION, 0, 4, "Test", HighlightColor.YELLOW)
            assertTrue(added.isEmpty())
        } finally {
            db.close()
        }
    }
}
