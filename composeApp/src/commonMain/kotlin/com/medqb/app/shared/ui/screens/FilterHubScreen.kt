package com.medqb.app.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.ui.screens.FilterPane
import com.medqb.app.shared.ui.dialogs.PerformanceFilterDialog
import com.medqb.app.shared.ui.dialogs.SubjectFilterDialog
import com.medqb.app.shared.ui.dialogs.SystemFilterDialog
import com.medqb.app.shared.ui.screens.history.HistoryPane
import com.medqb.app.shared.viewmodel.FilterHubViewModel

@Composable
internal fun FilterHubScreen(
    viewModel: FilterHubViewModel,
    onStartQuiz: () -> Unit,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onLoggingToggle: (Boolean) -> Unit,
    onSubmissionModeToggle: (SubmissionMode) -> Unit,
    onShowSnackbar: suspend (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val performanceLabel = formatPerformanceLabel(state.performanceFilter)

    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }

    var historySelectionMode by rememberSaveable { mutableStateOf(false) }

    FilterPaneScaffold(
        selectedPane = state.activePane,
        onPaneSelected = { viewModel.setActivePane(it) },
        showPaneToolbar = !historySelectionMode || state.activePane == FilterPane.Filters,
    ) {
        when (state.activePane) {
            FilterPane.Filters -> {
                FilterScreen(
                    databaseName = state.databaseName,
                    subjectCount = state.selectedSubjectIds.size,
                    systemCount = state.selectedSystemIds.size,
                    performanceFilter = state.performanceFilter,
                    performanceLabel = performanceLabel,
                    previewCount = state.previewQuestionCount,
                    isLoggingEnabled = state.isLoggingEnabled,
                    onLoggingToggle = onLoggingToggle,
                    submissionMode = state.submissionMode,
                    onSubmissionModeToggle = onSubmissionModeToggle,
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
            }
            FilterPane.History -> {
                HistoryPane(
                    historyEntries = state.historyEntries,
                    onHistorySelected = onHistorySelected,
                    onDeleteHistoryEntries = { viewModel.deleteHistoryEntries(it) },
                    onRenameHistoryEntry = { id, name -> viewModel.renameHistoryEntry(id, name) },
                    onCopyAllQids = { entries, onCopied ->
                        viewModel.copyQuestionIdsForHistoryEntries(entries, onCopied)
                    },
                    onSelectionModeChanged = { historySelectionMode = it },
                    onShowSnackbar = onShowSnackbar,
                )
            }
        }
    }

    if (showSubjectDialog) {
        SubjectFilterDialog(
            isVisible = true,
            resource = state.subjectsResource,
            selectedIds = state.selectedSubjectIds,
            onRetry = { viewModel.fetchSubjects() },
            onApply = { selected ->
                viewModel.applySelectedSubjects(selected)
                showSubjectDialog = false
            },
            onClear = {
                viewModel.applySelectedSubjects(emptySet())
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
                viewModel.applySelectedSystems(selected)
                showSystemDialog = false
            },
            onClear = {
                viewModel.applySelectedSystems(emptySet())
                showSystemDialog = false
            },
            onDismiss = { showSystemDialog = false }
        )
    }

    if (showPerformanceDialog) {
        PerformanceFilterDialog(
            current = state.performanceFilter,
            onSelect = { filter ->
                viewModel.setPerformanceFilter(filter)
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
