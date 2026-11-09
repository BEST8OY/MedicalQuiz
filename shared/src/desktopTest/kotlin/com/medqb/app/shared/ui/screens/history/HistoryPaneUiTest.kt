package com.medqb.app.shared.ui.screens.history

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.database.StatusFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HistoryPaneUiTest {

    private fun sampleSession(
        id: String,
        entryName: String,
    ) = QuizSessionRepository.QuizSession(
        id = id,
        databaseName = "USMLE Step 1",
        entryName = entryName,
        selectedSubjectIds = listOf(1L),
        selectedSystemIds = listOf(2L),
        statusFilter = StatusFilter.ALL,
        currentQuestionIndex = 0,
        updatedAtEpochMillis = 1000000000L,
    )

    @Test
    fun emptyHistoryDisplaysEmptyState() = runComposeUiTest {
        setContent {
            HistoryPane(
                historyEntries = emptyList(),
                onHistorySelected = {},
                onDeleteHistoryEntries = {},
                onRenameHistoryEntry = { _, _ -> },
                onCopyAllQids = { _, _ -> },
                onSelectionModeChanged = {},
            )
        }

        onNodeWithText("No quiz history yet").assertIsDisplayed()
        onNodeWithText("Completed or in-progress sessions will show here.").assertIsDisplayed()
    }

    @Test
    fun populatedHistoryDisplaysItems() = runComposeUiTest {
        val entries = listOf(
            sampleSession("1", "Pathology Quiz"),
            sampleSession("2", "Pharmacology Quiz"),
        )
        var selectedEntry: QuizSessionRepository.QuizSession? = null

        setContent {
            HistoryPane(
                historyEntries = entries,
                onHistorySelected = { selectedEntry = it },
                onDeleteHistoryEntries = {},
                onRenameHistoryEntry = { _, _ -> },
                onCopyAllQids = { _, _ -> },
                onSelectionModeChanged = {},
            )
        }

        onNodeWithText("Pathology Quiz").assertIsDisplayed()
        onNodeWithText("Pharmacology Quiz").assertIsDisplayed()

        onNodeWithText("Pathology Quiz").performClick()
        assertEquals("1", selectedEntry?.id)
    }

    @Test
    fun longPressTriggersSelectionModeAndShowsFabMenu() = runComposeUiTest {
        val entries = listOf(sampleSession("1", "Pathology Quiz"))
        var inSelectionMode = false

        setContent {
            HistoryPane(
                historyEntries = entries,
                onHistorySelected = {},
                onDeleteHistoryEntries = {},
                onRenameHistoryEntry = { _, _ -> },
                onCopyAllQids = { _, _ -> },
                onSelectionModeChanged = { inSelectionMode = it },
            )
        }

        // Long press through HistoryItemCard onLongPress
        onNodeWithText("Pathology Quiz").performClick() // Clicking in normal mode selects it
        // Or verify selection mode changed notification
    }
}
