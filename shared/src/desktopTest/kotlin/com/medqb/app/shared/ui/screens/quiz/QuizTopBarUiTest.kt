package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QuizTopBarUiTest {

    @Test
    fun displaysMarkButtonWhenEnabledAndHandlesClicks() = runComposeUiTest {
        var toggled = false

        setContent {
            TopBar(
                title = "Cardiology",
                isMarked = false,
                canMark = true,
                onToggleMark = { toggled = true },
                onNavigateBack = {},
                onResetLogClick = {},
                onSettingsClick = {},
            )
        }

        onNodeWithTag("quiz_mark_button").assertIsDisplayed().assertIsEnabled()
        onNodeWithContentDescription("Mark question").assertIsDisplayed()

        onNodeWithTag("quiz_mark_button").performClick()
        assertTrue(toggled, "Clicking mark button should invoke onToggleMark")
    }

    @Test
    fun displaysMarkedStateWithUnmarkContentDescription() = runComposeUiTest {
        setContent {
            TopBar(
                title = "Cardiology",
                isMarked = true,
                canMark = true,
                onToggleMark = {},
                onNavigateBack = {},
                onResetLogClick = {},
                onSettingsClick = {},
            )
        }

        onNodeWithTag("quiz_mark_button").assertIsDisplayed().assertIsEnabled()
        onNodeWithContentDescription("Unmark question").assertIsDisplayed()
    }

    @Test
    fun markButtonDisabledWhenCanMarkIsFalse() = runComposeUiTest {
        setContent {
            TopBar(
                title = "Cardiology",
                isMarked = false,
                canMark = false,
                onToggleMark = {},
                onNavigateBack = {},
                onResetLogClick = {},
                onSettingsClick = {},
            )
        }

        onNodeWithTag("quiz_mark_button").assertIsDisplayed().assertIsNotEnabled()
    }
}
