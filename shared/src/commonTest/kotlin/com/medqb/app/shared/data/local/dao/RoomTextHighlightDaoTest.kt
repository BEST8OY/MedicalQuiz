package com.medqb.app.shared.data.local.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.UserDatabaseConstructor
import com.medqb.app.shared.data.local.entity.TextHighlightEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomTextHighlightDaoTest {

    private fun buildDb(): UserDatabase =
        Room.inMemoryDatabaseBuilder<UserDatabase> { UserDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()

    private fun highlight(
        dbName: String = "db",
        qid: Long = 1L,
        section: String = "QUESTION",
        blockKey: String = "p:0",
        start: Int = 0,
        end: Int = 10,
        text: String = "sample",
        color: String = "YELLOW",
        createdAt: Long = 100L
    ) = TextHighlightEntity(
        dbName = dbName,
        questionId = qid,
        section = section,
        blockKey = blockKey,
        startOffset = start,
        endOffset = end,
        highlightedText = text,
        color = color,
        createdAt = createdAt
    )

    @Test
    fun insertAndQueryBySectionAndAllForQuestion() = runTest {
        val db = buildDb()
        try {
            val dao = db.textHighlightDao()

            val id1 = dao.insert(highlight(qid = 10L, section = "QUESTION", blockKey = "p:0", start = 5, end = 15, text = "first"))
            val id2 = dao.insert(highlight(qid = 10L, section = "QUESTION", blockKey = "p:0", start = 0, end = 4, text = "zero"))
            val id3 = dao.insert(highlight(qid = 10L, section = "QUESTION", blockKey = "p:1", start = 0, end = 4, text = "secondBlock"))
            val id4 = dao.insert(highlight(qid = 10L, section = "EXPLANATION", blockKey = "p:0", start = 0, end = 10, text = "expl"))
            val id5 = dao.insert(highlight(qid = 20L, section = "QUESTION", blockKey = "p:0", start = 0, end = 5, text = "other"))

            assertTrue(id1 > 0 && id2 > 0 && id3 > 0 && id4 > 0 && id5 > 0)

            // getBySection orders by block_key, start_offset ASC
            val questionSection = dao.getBySection("db", 10L, "QUESTION")
            assertEquals(3, questionSection.size)
            assertEquals("zero", questionSection[0].highlightedText)
            assertEquals("first", questionSection[1].highlightedText)
            assertEquals("secondBlock", questionSection[2].highlightedText)

            // getAllForQuestion orders by section, block_key, start_offset
            val allQ10 = dao.getAllForQuestion("db", 10L)
            assertEquals(4, allQ10.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun replaceWithMergedDeletesOldAndInsertsNewAtomically() = runTest {
        val db = buildDb()
        try {
            val dao = db.textHighlightDao()

            val id1 = dao.insert(highlight(qid = 1L, start = 0, end = 5, text = "Hello"))
            val id2 = dao.insert(highlight(qid = 1L, start = 6, end = 11, text = "World"))

            val mergedEntity = highlight(qid = 1L, start = 0, end = 11, text = "Hello World")
            val newId = dao.replaceWithMerged(listOf(id1, id2), mergedEntity)

            val remaining = dao.getAllForQuestion("db", 1L)
            assertEquals(1, remaining.size)
            assertEquals(newId, remaining[0].id)
            assertEquals("Hello World", remaining[0].highlightedText)
        } finally {
            db.close()
        }
    }

    @Test
    fun updateColorAndClearOperations() = runTest {
        val db = buildDb()
        try {
            val dao = db.textHighlightDao()

            val id = dao.insert(highlight(qid = 1L, color = "YELLOW"))
            dao.updateColor(id, "GREEN")

            val updated = dao.getAllForQuestion("db", 1L)
            assertEquals("GREEN", updated[0].color)

            // Clear for specific section
            dao.insert(highlight(qid = 1L, section = "EXPLANATION"))
            dao.clearForQuestion("db", 1L, "QUESTION")

            val afterClearQuestionSection = dao.getAllForQuestion("db", 1L)
            assertEquals(1, afterClearQuestionSection.size)
            assertEquals("EXPLANATION", afterClearQuestionSection[0].section)

            // Clear all for question
            dao.clearForQuestion("db", 1L, null)
            assertTrue(dao.getAllForQuestion("db", 1L).isEmpty())
        } finally {
            db.close()
        }
    }
}
