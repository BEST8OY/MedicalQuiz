package com.medqb.app.shared.ui.screens.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HistoryItemCardUiTest {

    private fun sampleSession(
        id: String = "session-1",
        databaseName: String = "USMLE Step 1",
        entryName: String = "Cardiology Review",
        currentQuestionIndex: Int = 4,
    ) = QuizSessionRepository.QuizSession(
        id = id,
        databaseName = databaseName,
        entryName = entryName,
        selectedSubjectIds = listOf(1L),
        selectedSystemIds = listOf(2L),
        statusFilter = StatusFilter.ALL,
        currentQuestionIndex = currentQuestionIndex,
        updatedAtEpochMillis = 1000000000L,
        selectedDifficultyTiers = setOf(DifficultyTier.INTERMEDIATE),
    )

    @Test
    fun displaysEntryTitleAndDatabaseName() = runComposeUiTest {
        val session = sampleSession()

        setContent {
            HistoryItemCard(
                entry = session,
                isSelected = false,
                selectionModeEnabled = false,
                onClick = {},
                onLongPress = {},
                onSwipeDelete = {},
                onRename = {},
                onSelectChanged = {},
            )
        }

        onNodeWithText("Cardiology Review").assertIsDisplayed()
        onNodeWithText("USMLE Step 1").assertIsDisplayed()
    }

    @Test
    fun clickInvokesCallback() = runComposeUiTest {
        val session = sampleSession()
        var clicked = false

        setContent {
            HistoryItemCard(
                entry = session,
                isSelected = false,
                selectionModeEnabled = false,
                onClick = { clicked = true },
                onLongPress = {},
                onSwipeDelete = {},
                onRename = {},
                onSelectChanged = {},
            )
        }

        onNodeWithText("Cardiology Review").performClick()
        assertTrue(clicked)
    }

    @Test
    fun selectionModeInvokesClick() = runComposeUiTest {
        val session = sampleSession()
        var clicked = false

        setContent {
            HistoryItemCard(
                entry = session,
                isSelected = true,
                selectionModeEnabled = true,
                onClick = { clicked = true },
                onLongPress = {},
                onSwipeDelete = {},
                onRename = {},
                onSelectChanged = {},
            )
        }

        onNodeWithText("Cardiology Review").performClick()
        assertTrue(clicked)
    }

    @Test
    fun exposesAccessibilityCustomActionsForDeleteAndRename() = runComposeUiTest {
        val session = sampleSession()
        var deleted = false
        var renamed = false

        setContent {
            HistoryItemCard(
                entry = session,
                isSelected = false,
                selectionModeEnabled = false,
                onClick = {},
                onLongPress = {},
                onSwipeDelete = { deleted = true },
                onRename = { renamed = true },
                onSelectChanged = {},
            )
        }

        val cardNode = onNodeWithText("Cardiology Review")
        cardNode.assertIsDisplayed()

        val semanticsNode = cardNode.fetchSemanticsNode()
        val actions = semanticsNode.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }
        val deleteAction = actions.firstOrNull { it.label == "Delete" }
        val renameAction = actions.firstOrNull { it.label == "Rename" }

        assertTrue(deleteAction != null, "Delete action must be present in semantics")
        assertTrue(renameAction != null, "Rename action must be present in semantics")

        deleteAction.action()
        renameAction.action()

        assertTrue(deleted)
        assertTrue(renamed)
    }
}
