package com.medqb.app.shared.ui.dialogs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun displaysMarkedStripAndTargetBadgeWhenQuestionsMarked() = runComposeUiTest {
        setContent {
            JumpToDialog(
                totalQuestions = 10,
                currentIndex = 0,
                markedIndices = setOf(2, 5), // Questions 3 and 6
                onJumpTo = {},
                onDismiss = {},
            )
        }

        onNodeWithText("Marked in session (2)").assertIsDisplayed()
        onNodeWithTag("jump_marked_chip_3").assertIsDisplayed()
        onNodeWithTag("jump_marked_chip_6").assertIsDisplayed()

        // Stepper: 1 -> 2 -> 3
        onNodeWithContentDescription("Increase").performClick()
        onNodeWithContentDescription("Increase").performClick()

        // Target badge appears for Question 3
        onNodeWithTag("jump_target_marked_badge").assertIsDisplayed()
    }

    @Test
    fun clickingMarkedChipInvokesJumpTo() = runComposeUiTest {
        var jumpedTo: Int? = null

        setContent {
            JumpToDialog(
                totalQuestions = 10,
                currentIndex = 0,
                markedIndices = setOf(4),
                onJumpTo = { jumpedTo = it },
                onDismiss = {},
            )
        }

        onNodeWithTag("jump_marked_chip_5").performClick()
        assertEquals(4, jumpedTo)
    }

    @Test
    fun clickingCurrentMarkedChipInvokesDismissInsteadOfJump() = runComposeUiTest {
        var jumpedTo: Int? = null
        var dismissed = false

        setContent {
            JumpToDialog(
                totalQuestions = 10,
                currentIndex = 2,
                markedIndices = setOf(2),
                onJumpTo = { jumpedTo = it },
                onDismiss = { dismissed = true },
            )
        }

        onNodeWithTag("jump_marked_chip_3").performClick()
        assertEquals(null, jumpedTo)
        assertTrue(dismissed)
    }

    @Test
    fun clickingNextMarkedButtonJumpsToNextMarkedQuestionWithWrapAround() = runComposeUiTest {
        var jumpedTo: Int? = null

        // 1. Next marked when current is 1 and marked are 0 and 3 -> jumps forward to 3
        setContent {
            JumpToDialog(
                totalQuestions = 10,
                currentIndex = 1,
                markedIndices = setOf(0, 3),
                onJumpTo = { jumpedTo = it },
                onDismiss = {},
            )
        }

        onNodeWithTag("jump_next_marked_button").performClick()
        assertEquals(3, jumpedTo)
    }

    @Test
    fun clickingNextMarkedButtonWrapsAroundToFirstMarked() = runComposeUiTest {
        var jumpedTo: Int? = null

        // 2. Next marked when current is 5 (past 3) and marked are 0 and 3 -> wraps around to 0
        setContent {
            JumpToDialog(
                totalQuestions = 10,
                currentIndex = 5,
                markedIndices = setOf(0, 3),
                onJumpTo = { jumpedTo = it },
                onDismiss = {},
            )
        }

        onNodeWithTag("jump_next_marked_button").performClick()
        assertEquals(0, jumpedTo)
    }

    @Test
    fun nextMarkedButtonDisabledWhenCurrentIsSoleMarkedQuestion() = runComposeUiTest {
        setContent {
            JumpToDialog(
                totalQuestions = 10,
                currentIndex = 2,
                markedIndices = setOf(2),
                onJumpTo = {},
                onDismiss = {},
            )
        }

        onNodeWithTag("jump_next_marked_button").assertIsNotEnabled()
    }
}
