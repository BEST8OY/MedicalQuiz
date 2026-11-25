package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.Question
import com.medqb.app.shared.ui.adaptive.ProvideAdaptiveLayoutInfo
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.state.QuizUiState
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QuizAdaptiveUiTest {

    @Test
    fun expandedWidthPlacesQuestionAndAnswersSideBySide() = runComposeUiTest {
        val sampleQuestion = Question(
            id = 101L,
            question = "<p>Question stem vignette</p>",
            explanation = "<p>Explanation body</p>",
            corrAns = 1,
            title = "Chest Pain",
            mediaName = null,
            otherMedias = null,
            pplTaken = 100.0,
            corrTaken = 75.0,
            subId = "1",
            sysId = "1",
        )
        val sampleAnswers = listOf(
            Answer(answerId = 1L, answerText = "<p>Option Alpha</p>", correctPercentage = 75, qId = 101L),
            Answer(answerId = 2L, answerText = "<p>Option Beta</p>", correctPercentage = 25, qId = 101L),
        )
        val state = QuizUiState(
            databaseName = "USMLE Step 1",
            questionIds = listOf(101L),
            currentQuestionIndex = 0,
            currentQuestion = sampleQuestion,
            currentAnswers = sampleAnswers,
            correctAnswerId = 1,
        )
        val mediaHandler = MediaHandler(
            onOpenMedia = { _, _ -> },
            onOpenHtml = {},
        )

        setContent {
            ProvideAdaptiveLayoutInfo(modifier = Modifier.requiredSize(1000.dp, 800.dp)) {
                QuizScreen(
                    state = state,
                    mediaHandler = mediaHandler,
                    onAnswerSelected = {},
                    onHighlightAdd = { _, _, _, _, _, _ -> },
                    onHighlightRemove = {},
                    onHighlightColorChange = { _, _ -> },
                )
            }
        }

        val questionNode = onNodeWithText("Question stem vignette").assertIsDisplayed()
        val answerNode = onNodeWithText("Option Alpha").assertIsDisplayed()

        val questionBounds = questionNode.fetchSemanticsNode().boundsInRoot
        val answerBounds = answerNode.fetchSemanticsNode().boundsInRoot

        assertTrue(
            answerBounds.left >= questionBounds.right,
            "Expected answer options (left=${answerBounds.left}) to sit to the right of question card (right=${questionBounds.right})"
        )
    }
}
