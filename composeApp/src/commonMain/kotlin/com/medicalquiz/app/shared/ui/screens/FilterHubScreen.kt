package com.medicalquiz.app.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.shared.data.QuizSessionRepository
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.ui.dialogs.PerformanceFilterDialog
import com.medicalquiz.app.shared.ui.dialogs.SubjectFilterDialog
import com.medicalquiz.app.shared.ui.dialogs.SystemFilterDialog
import com.medicalquiz.app.shared.ui.screens.history.HistoryPane
import com.medicalquiz.app.shared.viewmodel.QuizViewModel

@Composable
internal fun FilterHubScreen(
    viewModel: QuizViewModel,
    selectedPane: FilterPane,
    onPaneSelected: (FilterPane) -> Unit,
    historyEntries: List<QuizSessionRepository.QuizSession>,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onDeleteHistoryEntries: (Set<String>) -> Unit,
    onRenameHistoryEntry: (String, String) -> Unit,
    onStartQuiz: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val performanceLabel = formatPerformanceLabel(state.performanceFilter)

    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }
    var historySelectionMode by rememberSaveable { mutableStateOf(false) }

    FilterPaneScaffold(
        selectedPane = selectedPane,
        onPaneSelected = onPaneSelected,
        showPaneToolbar = !(selectedPane == FilterPane.History && historySelectionMode),
        filterContent = {
            FilterScreen(
                databaseName = state.databaseName,
                subjectCount = state.selectedSubjectIds.size,
                systemCount = state.selectedSystemIds.size,
                performanceLabel = performanceLabel,
                previewCount = state.previewQuestionCount,
                bottomContentPadding = 112.dp,
                onSelectSubjects = {
                    showSubjectDialog = true
                    viewModel.fetchSubjects()
                },
                onSelectSystems = {
                    showSystemDialog = true
                    val subjects = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                    viewModel.fetchSystemsForSubjects(subjects)
                },
                onSelectPerformance = { showPerformanceDialog = true },
                onStart = onStartQuiz,
                onClearFilters = {
                    viewModel.clearAllFilters()
                }
            )
        },
        historyContent = {
            HistoryPane(
                historyEntries = historyEntries,
                onHistorySelected = onHistorySelected,
                onDeleteHistoryEntries = onDeleteHistoryEntries,
                onRenameHistoryEntry = onRenameHistoryEntry,
                onCopyAllQids = { selectedEntries ->
                    viewModel.getQuestionIdsForHistoryEntries(selectedEntries)
                },
                onSelectionModeChanged = { historySelectionMode = it },
            )
        },
    )

    if (showSubjectDialog) {
        SubjectFilterDialog(
            isVisible = true,
            resource = state.subjectsResource,
            selectedIds = state.selectedSubjectIds,
            onRetry = { viewModel.fetchSubjects() },
            onApply = { selected ->
                viewModel.applySelectedSubjects(selected, loadQuestions = false)
                showSubjectDialog = false
            },
            onClear = {
                viewModel.applySelectedSubjects(emptySet(), loadQuestions = false)
                showSubjectDialog = false
            },
            onDismiss = { showSubjectDialog = false }
        )
    }

    if (showSystemDialog) {
        SystemFilterDialog(
            isVisible = true,
            resource = state.systemsResource,
            selectedIds = state.selectedSystemIds,
            onRetry = {
                val subjects = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
                viewModel.fetchSystemsForSubjects(subjects)
            },
            onApply = { selected ->
                viewModel.applySelectedSystems(selected, loadQuestions = false)
                showSystemDialog = false
            },
            onClear = {
                viewModel.applySelectedSystems(emptySet(), loadQuestions = false)
                showSystemDialog = false
            },
            onDismiss = { showSystemDialog = false }
        )
    }

    if (showPerformanceDialog) {
        PerformanceFilterDialog(
            current = state.performanceFilter,
            onSelect = { filter ->
                viewModel.setPerformanceFilter(filter, loadQuestions = false)
                showPerformanceDialog = false
            },
            onDismiss = { showPerformanceDialog = false }
        )
    }
}

@Composable
private fun formatPerformanceLabel(filter: PerformanceFilter): String {
    return when (filter) {
        PerformanceFilter.ALL -> "All Questions"
        PerformanceFilter.UNANSWERED -> "Not Attempted"
        PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
        PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
        PerformanceFilter.EVER_CORRECT -> "Ever Correct"
        PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
    }
}
