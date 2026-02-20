package com.medicalquiz.app.shared.ui.state

import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.data.models.Question
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuizUiStateTest {

    @Test
    fun `copyWithQuestion resets answer state when requested`() {
        val original = QuizUiState(
            selectedAnswerId = 3,
            answerSubmitted = true,
        )

        val updated = original.copyWithQuestion(
            question = sampleQuestion(),
            answers = listOf(sampleAnswer()),
            resetAnswerState = true,
        )

        assertNull(updated.selectedAnswerId)
        assertFalse(updated.answerSubmitted)
        assertEquals(1, updated.currentAnswers.size)
        assertEquals(1L, updated.currentQuestion?.id)
    }

    @Test
    fun `copyWithQuestion preserves answer state when reset disabled`() {
        val original = QuizUiState(
            selectedAnswerId = 2,
            answerSubmitted = true,
        )

        val updated = original.copyWithQuestion(
            question = sampleQuestion(),
            answers = listOf(sampleAnswer()),
            resetAnswerState = false,
        )

        assertEquals(2, updated.selectedAnswerId)
        assertTrue(updated.answerSubmitted)
    }

    @Test
    fun `navigation helpers reflect question position`() {
        val state = QuizUiState(
            questionIds = listOf(10L, 20L, 30L),
            currentQuestionIndex = 1,
        )

        assertEquals(3, state.totalQuestions)
        assertTrue(state.hasPreviousQuestion)
        assertTrue(state.hasNextQuestion)
    }

    private fun sampleQuestion() = Question(
        id = 1L,
        question = "Question",
        explanation = "Explanation",
        corrAns = 1,
        title = null,
        mediaName = null,
        otherMedias = null,
        pplTaken = null,
        corrTaken = null,
        subId = null,
        sysId = null,
    )

    private fun sampleAnswer() = Answer(
        answerId = 1L,
        answerText = "Answer",
        correctPercentage = null,
        qId = 1L,
    )
}
