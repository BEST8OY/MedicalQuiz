package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QuizStudyActionBarUiTest {

    @Test
    fun displaysAllStudyButtonsAndHandlesClicks() = runComposeUiTest {
        var keyInfoToggled = false
        var attendingTipToggled = false
        var labsOpened = false

        setContent {
            QuizStudyActionBar(
                isKeyInfoActive = false,
                onToggleKeyInfo = { keyInfoToggled = true },
                hasAttendingTip = true,
                isAttendingTipActive = false,
                onToggleAttendingTip = { attendingTipToggled = true },
                onOpenLabs = { labsOpened = true }
            )
        }

        onNodeWithText("KEY INFO").assertIsDisplayed().performClick()
        assertTrue(keyInfoToggled, "Clicking KEY INFO should invoke toggle")

        onNodeWithText("ATTENDING TIP").assertIsDisplayed().performClick()
        assertTrue(attendingTipToggled, "Clicking ATTENDING TIP should invoke toggle")

        onNodeWithText("LABS").assertIsDisplayed().performClick()
        assertTrue(labsOpened, "Clicking LABS should invoke open labs")
    }

    @Test
    fun attendingTipNotDisplayedWhenQuestionHasNoHint() = runComposeUiTest {
        setContent {
            QuizStudyActionBar(
                hasKeyInfo = true,
                isKeyInfoActive = false,
                onToggleKeyInfo = {},
                hasAttendingTip = false,
                isAttendingTipActive = false,
                onToggleAttendingTip = {},
                onOpenLabs = {}
            )
        }

        onNodeWithTag("quiz_action_attending_tip").assertDoesNotExist()
        onNodeWithTag("quiz_action_key_info").assertIsDisplayed()
        onNodeWithTag("quiz_action_labs").assertIsDisplayed()
    }

    @Test
    fun keyInfoNotDisplayedWhenQuestionHasNoKeyInfo() = runComposeUiTest {
        setContent {
            QuizStudyActionBar(
                hasKeyInfo = false,
                isKeyInfoActive = false,
                onToggleKeyInfo = {},
                hasAttendingTip = true,
                isAttendingTipActive = false,
                onToggleAttendingTip = {},
                onOpenLabs = {}
            )
        }

        onNodeWithTag("quiz_action_key_info").assertDoesNotExist()
        onNodeWithTag("quiz_action_attending_tip").assertIsDisplayed()
        onNodeWithTag("quiz_action_labs").assertIsDisplayed()
    }

    @Test
    fun attendingTipSectionRendersAndCanBeDismissed() = runComposeUiTest {
        var dismissed = false

        setContent {
            AttendingTipSection(
                isVisible = true,
                onDismiss = { dismissed = true },
                hintHtml = "<p>Look closely at serum sodium and glucose.</p>",
                linkHandler = {},
                mediaClick = {},
                showSelectedHighlight = false
            )
        }

        onNodeWithText("Attending's Tip").assertIsDisplayed()
        onNodeWithText("Look closely at serum sodium and glucose.").assertIsDisplayed()

        onNodeWithTag("attending_tip_close").assertIsDisplayed().performClick()
        assertTrue(dismissed, "Clicking close on Attending's Tip card should invoke dismiss")
    }
}
