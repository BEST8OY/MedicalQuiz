package com.medqb.app.shared.ui.screens.filter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.dialogs.PerformanceFilterDialog
import com.medqb.app.shared.ui.dialogs.SubjectFilterDialog
import com.medqb.app.shared.ui.dialogs.SystemFilterDialog
import com.medqb.app.shared.ui.screens.history.HistoryPane
import com.medqb.app.shared.ui.theme.ScreenLayout
import com.medqb.app.shared.viewmodel.FilterHubViewModel

import com.medqb.app.shared.ui.state.FilterUiState

@Composable
internal fun FilterHubScreen(
    viewModel: FilterHubViewModel,
    onStartQuiz: () -> Unit,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onLoggingToggle: (Boolean) -> Unit,
    onSubmissionModeToggle: (SubmissionMode) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
    onDismissSnackbar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FilterHubContent(
        state = state,
        onStartQuiz = onStartQuiz,
        onHistorySelected = onHistorySelected,
        onLoggingToggle = onLoggingToggle,
        onSubmissionModeToggle = onSubmissionModeToggle,
        onPaneSelected = { pane ->
            if (pane != state.activePane) {
                onDismissSnackbar()
                viewModel.setActivePane(pane)
            }
        },
        onClearFilters = { viewModel.clearAllFilters() },
        onDeleteHistoryEntries = { viewModel.deleteHistoryEntries(it) },
        onRenameHistoryEntry = { id, name -> viewModel.renameHistoryEntry(id, name) },
        onCopyAllQids = { entries, onCopied -> viewModel.copyQuestionIdsForHistoryEntries(entries, onCopied) },
        onUndoDelete = { viewModel.undoHistoryEntry(it) },
        onFetchSubjects = { viewModel.fetchSubjects() },
        onApplySelectedSubjects = { viewModel.applySelectedSubjects(it) },
        onFetchSystems = { viewModel.fetchSystems() },
        onApplySelectedSystems = { viewModel.applySelectedSystems(it) },
        onSelectPerformanceFilter = { viewModel.setPerformanceFilter(it) },
        onShowSnackbar = onShowSnackbar,
        onDismissSnackbar = onDismissSnackbar,
        modifier = modifier,
    )
}

@Composable
internal fun FilterHubContent(
    state: FilterUiState,
    onStartQuiz: () -> Unit,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onLoggingToggle: (Boolean) -> Unit,
    onSubmissionModeToggle: (SubmissionMode) -> Unit,
    onPaneSelected: (FilterPane) -> Unit,
    onClearFilters: () -> Unit,
    onDeleteHistoryEntries: suspend (Set<String>) -> Unit,
    onRenameHistoryEntry: (String, String) -> Unit,
    onCopyAllQids: (List<QuizSessionRepository.QuizSession>, (String) -> Unit) -> Unit,
    onUndoDelete: suspend (QuizSessionRepository.QuizSession) -> Unit,
    onFetchSubjects: () -> Unit,
    onApplySelectedSubjects: (Set<Long>) -> Unit,
    onFetchSystems: () -> Unit,
    onApplySelectedSystems: (Set<Long>) -> Unit,
    onSelectPerformanceFilter: (PerformanceFilter) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
    onDismissSnackbar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val performanceLabel = formatPerformanceLabel(state.performanceFilter)

    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }

    var historySelectionMode by rememberSaveable { mutableStateOf(false) }

    FilterPaneScaffold(
        selectedPane = state.activePane,
        onPaneSelected = onPaneSelected,
        showPaneToolbar = !historySelectionMode || state.activePane == FilterPane.Filters,
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            val bottomContentPadding = if (screenHeight < ScreenLayout.CompactHeightBreakpoint) ScreenLayout.BottomPaddingCompact else ScreenLayout.BottomPaddingDefault
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = state.activePane,
                transitionSpec = {
                    fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                        fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                },
                label = "filter_pane_content",
            ) { activePane ->
                when (activePane) {
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
                            bottomContentPadding = bottomContentPadding,
                            onSelectSubjects = { showSubjectDialog = true },
                            onSelectSystems = { showSystemDialog = true },
                            onSelectPerformance = { showPerformanceDialog = true },
                            onStart = {
                                onDismissSnackbar()
                                onStartQuiz()
                            },
                            onClearFilters = onClearFilters,
                        )
                    }
                    FilterPane.History -> {
                        HistoryPane(
                            historyEntries = state.historyEntries,
                            onHistorySelected = { entry ->
                                onDismissSnackbar()
                                onHistorySelected(entry)
                            },
                            onDeleteHistoryEntries = onDeleteHistoryEntries,
                            onRenameHistoryEntry = onRenameHistoryEntry,
                            onCopyAllQids = onCopyAllQids,
                            onSelectionModeChanged = { historySelectionMode = it },
                            onUndoDelete = onUndoDelete,
                            onShowSnackbar = onShowSnackbar,
                            onDismissSnackbar = onDismissSnackbar,
                        )
                    }
                }
            }
        }
    }

    if (showSubjectDialog) {
        SubjectFilterDialog(
            resource = state.subjectsResource,
            selectedIds = state.selectedSubjectIds,
            onRetry = onFetchSubjects,
            onApply = { selected ->
                onApplySelectedSubjects(selected)
                showSubjectDialog = false
            },
            onDismiss = { showSubjectDialog = false }
        )
    }

    if (showSystemDialog) {
        SystemFilterDialog(
            resource = state.systemsResource,
            selectedIds = state.selectedSystemIds,
            onRetry = onFetchSystems,
            onApply = { selected ->
                onApplySelectedSystems(selected)
                showSystemDialog = false
            },
            onDismiss = { showSystemDialog = false }
        )
    }

    if (showPerformanceDialog) {
        PerformanceFilterDialog(
            current = state.performanceFilter,
            onSelect = { filter ->
                onSelectPerformanceFilter(filter)
                showPerformanceDialog = false
            },
            onDismiss = { showPerformanceDialog = false }
        )
    }
}

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
