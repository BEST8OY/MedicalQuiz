package com.medqb.app.shared.ui.dialogs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.medqb.app.shared.data.database.DifficultyTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DifficultyFilterBottomSheetUiTest {

    @Test
    fun displaysAllTiersAndHandlesSelectionAndApply() = runComposeUiTest {
        var appliedTiers: Set<DifficultyTier>? = null
        var dismissed = false

        val testCounts = mapOf(
            DifficultyTier.VERY_EASY to 100,
            DifficultyTier.EASY to 250,
            DifficultyTier.INTERMEDIATE to 400,
            DifficultyTier.DIFFICULT to 180,
            DifficultyTier.VERY_DIFFICULT to 70,
        )

        setContent {
            DifficultyFilterBottomSheet(
                selectedTiers = setOf(DifficultyTier.EASY),
                counts = testCounts,
                onApply = { appliedTiers = it },
                onDismiss = { dismissed = true },
            )
        }

        // Header
        onNodeWithText("Filter by difficulty").assertIsDisplayed()
        onNodeWithText("Select one or more difficulty tiers").assertIsDisplayed()

        // Verify all 5 tiers displayed
        DifficultyTier.entries.forEach { tier ->
            onNodeWithText(tier.displayName).assertIsDisplayed()
        }

        // Toggle Intermediate on
        onNodeWithText("Intermediate").performClick()

        // Click Apply
        onNodeWithText("Apply").performClick()

        assertEquals(
            setOf(DifficultyTier.EASY, DifficultyTier.INTERMEDIATE),
            appliedTiers
        )
        assertFalse(dismissed)
    }

    @Test
    fun selectAllAndClearAllWorkCorrectly() = runComposeUiTest {
        var appliedTiers: Set<DifficultyTier>? = null

        setContent {
            DifficultyFilterBottomSheet(
                selectedTiers = emptySet(),
                counts = emptyMap(),
                onApply = { appliedTiers = it },
                onDismiss = {},
            )
        }

        onNodeWithText("Clear All").assertIsNotEnabled()
        onNodeWithText("Select All").assertIsEnabled().performClick()

        onNodeWithText("Select All").assertIsNotEnabled()
        onNodeWithText("Clear All").assertIsEnabled()

        // Applying when all are selected normalizes to emptySet() ("All Difficulties")
        onNodeWithText("Apply").performClick()
        assertEquals(emptySet(), appliedTiers)
    }

    @Test
    fun cancelInvokesDismiss() = runComposeUiTest {
        var dismissed = false

        setContent {
            DifficultyFilterBottomSheet(
                selectedTiers = emptySet(),
                counts = emptyMap(),
                onApply = {},
                onDismiss = { dismissed = true },
            )
        }

        onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }
}
