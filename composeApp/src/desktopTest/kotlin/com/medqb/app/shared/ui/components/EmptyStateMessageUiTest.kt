package com.medqb.app.shared.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class EmptyStateMessageUiTest {

    @Test
    fun displaysTitleAndSubtitle() = runComposeUiTest {
        setContent {
            EmptyStateMessage(
                title = "No Questions Available",
                subtitle = "Select another category to continue",
            )
        }

        onNodeWithText("No Questions Available").assertIsDisplayed()
        onNodeWithText("Select another category to continue").assertIsDisplayed()
    }
}
