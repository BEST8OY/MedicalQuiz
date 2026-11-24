package com.medqb.app.shared.ui.screens.filter

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.ui.adaptive.ProvideAdaptiveLayoutInfo
import com.medqb.app.shared.ui.state.FilterUiState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FilterHubAdaptiveUiTest {

    private fun sampleState(activePane: FilterPane = FilterPane.Filters): FilterUiState = FilterUiState(
        databaseName = "USMLE Step 1",
        previewQuestionCount = 42,
        activePane = activePane,
        historyEntries = listOf(
            QuizSessionRepository.QuizSession(
                id = "session-1",
                databaseName = "USMLE Step 1",
                entryName = "Cardiology Review",
                selectedSubjectIds = listOf(1L),
                selectedSystemIds = listOf(2L),
                statusFilter = StatusFilter.ALL,
                currentQuestionIndex = 0,
                updatedAtEpochMillis = 1_700_000_000_000L,
            )
        ),
    )

    @Test
    fun compactWidthShowsFloatingToolbarAndOnlyActivePane() = runComposeUiTest {
        setContent {
            ProvideAdaptiveLayoutInfo(modifier = Modifier.requiredSize(400.dp, 800.dp)) {
                FilterHubContent(
                    state = sampleState(activePane = FilterPane.Filters),
                    onStartQuiz = {},
                    onHistorySelected = {},
                    onLoggingToggle = {},
                    onSubmissionModeToggle = {},
                    onPaneSelected = {},
                    onClearFilters = {},
                    onDeleteHistoryEntries = {},
                    onRenameHistoryEntry = { _, _ -> },
                    onCopyAllQids = { _, _ -> },
                    onUndoDelete = {},
                    onFetchSubjects = {},
                    onApplySelectedSubjects = { _, _ -> },
                    onFetchSystems = {},
                    onApplySelectedSystems = { _, _ -> },
                    onSelectStatusFilter = {},
                )
            }
        }

        onNodeWithText("Filter").assertIsDisplayed()
        onNodeWithText("History").assertIsDisplayed()
        onNodeWithText("Subjects").assertIsDisplayed()
        onNodeWithText("Cardiology Review").assertDoesNotExist()
    }

    @Test
    fun expandedWidthShowsBothPanesSimultaneouslyAndHidesToolbar() = runComposeUiTest {
        setContent {
            ProvideAdaptiveLayoutInfo(modifier = Modifier.requiredSize(1000.dp, 800.dp)) {
                FilterHubContent(
                    state = sampleState(activePane = FilterPane.Filters),
                    onStartQuiz = {},
                    onHistorySelected = {},
                    onLoggingToggle = {},
                    onSubmissionModeToggle = {},
                    onPaneSelected = {},
                    onClearFilters = {},
                    onDeleteHistoryEntries = {},
                    onRenameHistoryEntry = { _, _ -> },
                    onCopyAllQids = { _, _ -> },
                    onUndoDelete = {},
                    onFetchSubjects = {},
                    onApplySelectedSubjects = { _, _ -> },
                    onFetchSystems = {},
                    onApplySelectedSystems = { _, _ -> },
                    onSelectStatusFilter = {},
                )
            }
        }

        onNodeWithText("Subjects").assertIsDisplayed()
        onNodeWithText("Cardiology Review").assertIsDisplayed()
        onNodeWithText("Filter").assertDoesNotExist()
        onNodeWithText("History").assertDoesNotExist()
    }
}
