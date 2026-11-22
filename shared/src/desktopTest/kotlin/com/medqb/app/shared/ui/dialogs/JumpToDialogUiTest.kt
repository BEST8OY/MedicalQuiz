package com.medqb.app.shared.ui.dialogs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class JumpToDialogUiTest {

    @Test
    fun displaysDialogHeaderAndCurrentPosition() = runComposeUiTest {
        setContent {
            JumpToDialog(
                totalQuestions = 25,
                currentIndex = 4, // 0-based index 4 -> Question 5
                onJumpTo = {},
                onDismiss = {},
            )
        }

        onNodeWithText("Jump to question").assertIsDisplayed()
        onNodeWithText("Currently on 5 of 25").assertIsDisplayed()
        onNodeWithText("Jump").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun stepperButtonsAndJumpAction() = runComposeUiTest {
        var jumpedTo: Int? = null

        setContent {
            JumpToDialog(
                totalQuestions = 25,
                currentIndex = 4, // starts on 5
                onJumpTo = { jumpedTo = it },
                onDismiss = {},
            )
        }

        // Click increase button twice: 5 -> 6 -> 7
        onNodeWithContentDescription("Increase").performClick()
        onNodeWithContentDescription("Increase").performClick()

        // Click Jump button
        onNodeWithText("Jump").performClick()

        // Jumped to question 7 (0-based index 6)
        assertEquals(6, jumpedTo)
    }

    @Test
    fun cancelInvokesDismiss() = runComposeUiTest {
        var dismissed = false

        setContent {
            JumpToDialog(
                totalQuestions = 10,
                currentIndex = 2,
                onJumpTo = {},
                onDismiss = { dismissed = true },
            )
        }

        onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun stepperBoundariesDisableButtons() = runComposeUiTest {
        // At first question: Decrease is disabled
        setContent {
            JumpToDialog(
                totalQuestions = 10,
                currentIndex = 0, // question 1
                onJumpTo = {},
                onDismiss = {},
            )
        }

        onNodeWithContentDescription("Decrease").assertIsNotEnabled()
    }

    @Test
    fun stepperMaxBoundaryDisablesIncreaseButton() = runComposeUiTest {
        // At last question: Increase is disabled
        setContent {
            JumpToDialog(
                totalQuestions = 10,
                currentIndex = 9, // question 10
                onJumpTo = {},
                onDismiss = {},
            )
        }

        onNodeWithContentDescription("Increase").assertIsNotEnabled()
    }

    @Test
    fun directTextInputAndJump() = runComposeUiTest {
        var jumpedTo: Int? = null

        setContent {
            JumpToDialog(
                totalQuestions = 25,
                currentIndex = 0,
                onJumpTo = { jumpedTo = it },
                onDismiss = {},
            )
        }

        onNodeWithTag("jump_number_input").performTextReplacement("15")
        onNodeWithText("Jump").performClick()

        assertEquals(14, jumpedTo)
    }

    @Test
    fun invalidTotalQuestionsTriggersDismiss() = runComposeUiTest {
        var dismissed = false

        setContent {
            JumpToDialog(
                totalQuestions = 0,
                currentIndex = 0,
                onJumpTo = {},
                onDismiss = { dismissed = true },
            )
        }

        assertTrue(dismissed)
    }
}
