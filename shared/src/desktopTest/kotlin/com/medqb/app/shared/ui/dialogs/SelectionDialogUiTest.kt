package com.medqb.app.shared.ui.dialogs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.utils.Resource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SelectionDialogUiTest {

    @Test
    fun displaysSubjectDialogWithProperVerticalLayoutWithoutOverlap() = runComposeUiTest {
        val testSubjects = listOf(
            Subject(id = 1L, name = "Aging", count = 12),
            Subject(id = 2L, name = "Allergy and immunology", count = 25),
            Subject(id = 3L, name = "Anatomy", count = 50),
        )

        setContent {
            SubjectFilterDialog(
                resource = Resource.Success(testSubjects),
                selectedIds = emptySet(),
                excludedIds = emptySet(),
                onApply = { _, _ -> },
                onRetry = {},
                onDismiss = {},
            )
        }

        // Header
        val header = onNodeWithText("Select subjects").assertIsDisplayed()
        // Search bar
        val search = onNodeWithText("Search...").assertIsDisplayed()
        // Help banner
        val help = onNodeWithText("💡 Tap row to cycle:  [✓] Include  →  [✕] Exclude  →  Clear").assertIsDisplayed()
        // Subtitle & actions
        val subtitle = onNodeWithText("All items (none excluded)").assertIsDisplayed()
        onNodeWithText("Select all").assertIsDisplayed()
        // List items
        val aging = onNodeWithText("Aging").assertIsDisplayed()
        // Dialog actions
        val apply = onNodeWithText("Apply").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()

        // Verify vertical ordering to ensure NO overlapping
        val headerBounds = header.getUnclippedBoundsInRoot()
        val searchBounds = search.getUnclippedBoundsInRoot()
        val helpBounds = help.getUnclippedBoundsInRoot()
        val subtitleBounds = subtitle.getUnclippedBoundsInRoot()
        val agingBounds = aging.getUnclippedBoundsInRoot()
        val applyBounds = apply.getUnclippedBoundsInRoot()

        assertTrue(searchBounds.top >= headerBounds.bottom, "Search should be below header")
        assertTrue(helpBounds.top >= searchBounds.bottom, "Help banner should be below search")
        assertTrue(subtitleBounds.top >= helpBounds.bottom, "Subtitle row should be below help banner")
        assertTrue(agingBounds.top >= subtitleBounds.bottom, "List item should be below subtitle row")
        assertTrue(applyBounds.top >= agingBounds.bottom, "Apply button should be below list items")
    }

    @Test
    fun cyclesSelectionStateAndApplies() = runComposeUiTest {
        val testSubjects = listOf(
            Subject(id = 1L, name = "Aging", count = 12),
        )
        var appliedIncluded: Set<Long>? = null
        var appliedExcluded: Set<Long>? = null

        setContent {
            SubjectFilterDialog(
                resource = Resource.Success(testSubjects),
                selectedIds = emptySet(),
                excludedIds = emptySet(),
                onApply = { inc, exc ->
                    appliedIncluded = inc
                    appliedExcluded = exc
                },
                onRetry = {},
                onDismiss = {},
            )
        }

        // Click row once -> INCLUDED
        onNodeWithText("Aging").performClick()
        onNodeWithText("INCLUDE").assertIsDisplayed()

        // Click Apply
        onNodeWithText("Apply").performClick()
        assertEquals(setOf(1L), appliedIncluded)
        assertEquals(emptySet(), appliedExcluded)
    }
}
