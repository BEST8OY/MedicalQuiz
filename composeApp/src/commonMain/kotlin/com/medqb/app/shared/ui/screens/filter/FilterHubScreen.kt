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
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.dialogs.DifficultyFilterDialog
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
        onApplySelectedSubjects = { inc, excl -> viewModel.applySelectedSubjects(inc, excl) },
        onFetchSystems = { viewModel.fetchSystems() },
        onApplySelectedSystems = { inc, excl -> viewModel.applySelectedSystems(inc, excl) },
        onSelectPerformanceFilter = { viewModel.setPerformanceFilter(it) },
        onSelectDifficultyFilters = { viewModel.setDifficultyFilters(it) },
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
    onApplySelectedSubjects: (Set<Long>, Set<Long>) -> Unit,
    onFetchSystems: () -> Unit,
    onApplySelectedSystems: (Set<Long>, Set<Long>) -> Unit,
    onSelectPerformanceFilter: (PerformanceFilter) -> Unit,
    onSelectDifficultyFilters: (Set<DifficultyTier>) -> Unit = {},
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
    onDismissSnackbar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val performanceLabel = formatPerformanceLabel(state.performanceFilter)
    val difficultyLabel = formatDifficultyLabel(state.selectedDifficultyTiers)

    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }
    var showDifficultyDialog by rememberSaveable { mutableStateOf(false) }

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
                            excludedSubjectCount = state.excludedSubjectIds.size,
                            systemCount = state.selectedSystemIds.size,
                            excludedSystemCount = state.excludedSystemIds.size,
                            performanceFilter = state.performanceFilter,
                            performanceLabel = performanceLabel,
                            selectedDifficultyTiers = state.selectedDifficultyTiers,
                            difficultyLabel = difficultyLabel,
                            isDifficultyAvailable = state.isDifficultyAvailable,
                            previewCount = state.previewQuestionCount,
                            isLoggingEnabled = state.isLoggingEnabled,
                            onLoggingToggle = onLoggingToggle,
                            submissionMode = state.submissionMode,
                            onSubmissionModeToggle = onSubmissionModeToggle,
                            bottomContentPadding = bottomContentPadding,
                            onSelectSubjects = { showSubjectDialog = true },
                            onSelectSystems = { showSystemDialog = true },
                            onSelectPerformance = { showPerformanceDialog = true },
                            onSelectDifficulty = { showDifficultyDialog = true },
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
            excludedIds = state.excludedSubjectIds,
            onRetry = onFetchSubjects,
            onApply = { included, excluded ->
                onApplySelectedSubjects(included, excluded)
                showSubjectDialog = false
            },
            onDismiss = { showSubjectDialog = false }
        )
    }

    if (showSystemDialog) {
        SystemFilterDialog(
            resource = state.systemsResource,
            selectedIds = state.selectedSystemIds,
            excludedIds = state.excludedSystemIds,
            onRetry = onFetchSystems,
            onApply = { included, excluded ->
                onApplySelectedSystems(included, excluded)
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

    if (showDifficultyDialog) {
        DifficultyFilterDialog(
            selectedTiers = state.selectedDifficultyTiers,
            counts = state.difficultyCounts,
            onApply = { selected ->
                onSelectDifficultyFilters(selected)
                showDifficultyDialog = false
            },
            onDismiss = { showDifficultyDialog = false }
        )
    }
}

private fun formatPerformanceLabel(filter: PerformanceFilter): String {
    return when (filter) {
        PerformanceFilter.ALL -> "All Questions"
        PerformanceFilter.UNANSWERED -> "Not Attempted"
        PerformanceFilter.LAST_CORRECT -> "Last Correct"
        PerformanceFilter.LAST_INCORRECT -> "Last Incorrect"
        PerformanceFilter.EVER_CORRECT -> "Ever Correct"
        PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
    }
}

