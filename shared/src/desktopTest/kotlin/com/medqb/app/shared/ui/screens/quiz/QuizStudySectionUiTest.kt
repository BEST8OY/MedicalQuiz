package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QuizStudySectionUiTest {

    @Test
    fun displaysAllStudyButtonsAndHandlesClicks() = runComposeUiTest {
        var keyInfoToggled = false
        var attendingTipToggled = false
        var labsOpened = false

        setContent {
            QuizStudySection(
                isKeyInfoActive = false,
                onToggleKeyInfo = { keyInfoToggled = true },
                hasAttendingTip = true,
                isAttendingTipActive = false,
                onToggleAttendingTip = { attendingTipToggled = true },
                onOpenLabs = { labsOpened = true }
            )
        }

        onNodeWithText("Key info").assertIsDisplayed().performClick()
        assertTrue(keyInfoToggled, "Clicking Key info should invoke toggle")

        onNodeWithText("Attending tip").assertIsDisplayed().performClick()
        assertTrue(attendingTipToggled, "Clicking Attending tip should invoke toggle")

        onNodeWithText("Labs").assertIsDisplayed().performClick()
        assertTrue(labsOpened, "Clicking Labs should invoke open labs")
    }

    @Test
    fun attendingTipNotDisplayedWhenQuestionHasNoHint() = runComposeUiTest {
        setContent {
            QuizStudySection(
                hasKeyInfo = true,
                isKeyInfoActive = false,
                onToggleKeyInfo = {},
                hasAttendingTip = false,
                isAttendingTipActive = false,
                onToggleAttendingTip = {},
                onOpenLabs = {}
            )
        }

        onNodeWithText("Attending tip").assertDoesNotExist()
        onNodeWithText("Key info").assertIsDisplayed()
        onNodeWithText("Labs").assertIsDisplayed()
    }

    @Test
    fun keyInfoNotDisplayedWhenQuestionHasNoKeyInfo() = runComposeUiTest {
        setContent {
            QuizStudySection(
                hasKeyInfo = false,
                isKeyInfoActive = false,
                onToggleKeyInfo = {},
                hasAttendingTip = true,
                isAttendingTipActive = false,
                onToggleAttendingTip = {},
                onOpenLabs = {}
            )
        }

        onNodeWithText("Key info").assertDoesNotExist()
        onNodeWithText("Attending tip").assertIsDisplayed()
        onNodeWithText("Labs").assertIsDisplayed()
    }

    @Test
    fun attendingTipCardRendersAvatarAndContent() = runComposeUiTest {
        setContent {
            QuizStudySection(
                questionId = 123L,
                hasKeyInfo = true,
                isKeyInfoActive = false,
                onToggleKeyInfo = {},
                hasAttendingTip = true,
                isAttendingTipActive = true,
                onToggleAttendingTip = {},
                onOpenLabs = {},
                hintHtml = "<p>Look closely at serum sodium and glucose.</p>",
                linkHandler = {},
                mediaClick = {},
            )
        }

        onNodeWithTag("attending_tip_card").assertIsDisplayed()
        onNodeWithTag("attending_doctor_avatar").assertIsDisplayed()
        onNodeWithText("Look closely at serum sodium and glucose.").assertIsDisplayed()
        onNodeWithText("Attending's Tip").assertDoesNotExist()
        onNodeWithTag("attending_tip_close").assertDoesNotExist()
    }
}
