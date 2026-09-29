package com.medqb.app.shared.domain

import com.medqb.app.shared.data.TextHighlightsRepository
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.viewmodel.FakeDatabaseProvider
import com.medqb.app.shared.viewmodel.FakeTextHighlightsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadQuestionUseCaseTest {

    @Test
    fun invokeWithNullDbReturnsEmptyDetails() = runTest {
        val repo = FakeTextHighlightsRepository()
        val useCase = LoadQuestionUseCase(repo)

        val result = useCase(
            db = null,
            dbName = "bank",
            questionId = 1L,
            isLoggingEnabled = true,
        )

        assertNull(result.question)
        assertTrue(result.answers.isEmpty())
        assertNull(result.performance)
        assertNull(result.correctAnswerId)
    }

    @Test
    fun invokeSplitsQuestionAndExplanationHighlights() = runTest {
        val repo = FakeTextHighlightsRepository().apply {
            seedHighlights(
                dbName = "bank",
                questionId = 1L,
                highlights = listOf(
                    TextHighlight(1, "bank", 1L, HighlightSection.QUESTION, 0, 5, "Hello", HighlightColor.YELLOW, 0),
                    TextHighlight(2, "bank", 1L, HighlightSection.EXPLANATION, 0, 4, "Expl", HighlightColor.GREEN, 0),
                )
            )
        }
        val db = FakeDatabaseProvider("bank")
        val useCase = LoadQuestionUseCase(repo)

        val result = useCase(
            db = db,
            dbName = "bank",
            questionId = 1L,
            isLoggingEnabled = true,
        )

        assertNotNull(result.question)
        assertEquals(1, result.questionHighlights.size)
        assertEquals(1L, result.questionHighlights.first().id)
        assertEquals(1, result.explanationHighlights.size)
        assertEquals(2L, result.explanationHighlights.first().id)
    }

    @Test
    fun highlightRepositoryFailureDegradesGracefully() = runTest {
        val throwingRepo = object : TextHighlightsRepository {
            override suspend fun getHighlightsForQuestion(dbName: String, questionId: Long): List<TextHighlight> {
                throw RuntimeException("Database locked")
            }
            override suspend fun addHighlight(dbName: String, questionId: Long, section: HighlightSection, startOffset: Int, endOffset: Int, highlightedText: String, color: HighlightColor) = emptyList<TextHighlight>()
            override suspend fun removeHighlight(dbName: String, questionId: Long, highlightId: Long) = emptyList<TextHighlight>()
            override suspend fun updateHighlightColor(dbName: String, questionId: Long, highlightId: Long, color: HighlightColor) = emptyList<TextHighlight>()
        }
        val db = FakeDatabaseProvider("bank")
        val useCase = LoadQuestionUseCase(throwingRepo)

        val result = useCase(
            db = db,
            dbName = "bank",
            questionId = 1L,
            isLoggingEnabled = true,
        )

        assertNotNull(result.question)
        assertTrue(result.questionHighlights.isEmpty())
        assertTrue(result.explanationHighlights.isEmpty())
    }

    @Test
    fun nonExistentQuestionIdClearsPerformance() = runTest {
        val repo = FakeTextHighlightsRepository()
        val db = FakeDatabaseProvider("bank")
        val useCase = LoadQuestionUseCase(repo)

        val result = useCase(
            db = db,
            dbName = "bank",
            questionId = 999L, // question does not exist in FakeDatabaseProvider
            isLoggingEnabled = true,
        )

        assertNull(result.question)
        assertNull(result.performance)
    }
}
