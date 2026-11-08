package com.medqb.app.shared.ui.screens.quiz.labs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LabsBottomSheetUiTest {

    @Test
    fun displaysAllFourTabsAndSwitchesCategories() = runComposeUiTest {
        var dismissed = false

        setContent {
            LabsBottomSheet(
                onDismiss = { dismissed = true }
            )
        }

        // Verify bottom sheet title
        onNodeWithText("Normal Laboratory Values").assertIsDisplayed()

        // Verify all 4 tabs exist
        onNodeWithText("Serum").assertIsDisplayed()
        onNodeWithText("Cerebrospinal").assertIsDisplayed()
        onNodeWithText("Blood").assertIsDisplayed()
        onNodeWithText("Urine and BMI").assertIsDisplayed()

        // Initial tab is Serum
        onNodeWithText("Serum Reference Values").assertIsDisplayed()

        // Switch to Cerebrospinal
        onNodeWithText("Cerebrospinal").performClick()
        onNodeWithText("Cerebrospinal Reference Values").assertIsDisplayed()

        // Switch to Blood
        onNodeWithText("Blood").performClick()
        onNodeWithText("Blood Reference Values").assertIsDisplayed()

        // Switch to Urine and BMI
        onNodeWithText("Urine and BMI").performClick()
        onNodeWithText("Urine and BMI Reference Values").assertIsDisplayed()

        // Click close
        onNodeWithTag("labs_close_button").assertIsDisplayed().performClick()
        assertTrue(dismissed, "Clicking close button should invoke onDismiss")
    }
}
