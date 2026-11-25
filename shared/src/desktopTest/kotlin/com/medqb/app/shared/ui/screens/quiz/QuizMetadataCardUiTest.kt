package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.medqb.app.shared.data.models.Question
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class QuizMetadataCardUiTest {

    @Test
    fun rendersSystemContainingCommasAsSingleIntactChip() = runComposeUiTest {
        val question = Question(
            id = 77L,
            question = "",
            explanation = "",
            corrAns = 1,
            title = null,
            mediaName = null,
            otherMedias = null,
            pplTaken = null,
            corrTaken = null,
            subId = "1",
            sysId = "30",
            subNames = listOf("Medicine"),
            sysNames = listOf("Blood, Lymphatic, and Immune Systems"),
        )

        setContent {
            QuestionMetadataCard(sections = computeMetadataSections(question))
        }

        onNodeWithText("System").assertIsDisplayed()
        onNodeWithText("Systems").assertDoesNotExist()
        onNodeWithText("Blood, Lymphatic, and Immune Systems").assertIsDisplayed()
        onNodeWithText("Blood").assertDoesNotExist()
    }
}
