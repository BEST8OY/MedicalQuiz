package com.medqb.app.shared.ui.screens.filter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.adaptive.LocalAdaptiveLayoutInfo
import com.medqb.app.shared.ui.dialogs.DifficultyFilterBottomSheet
import com.medqb.app.shared.ui.dialogs.StatusFilterBottomSheet
import com.medqb.app.shared.ui.dialogs.SubjectFilterBottomSheet
import com.medqb.app.shared.ui.dialogs.SystemFilterBottomSheet
import com.medqb.app.shared.ui.screens.history.HistoryPane
import com.medqb.app.shared.ui.state.FilterUiState
import com.medqb.app.shared.ui.theme.exitSpatialSpec
import com.medqb.app.shared.viewmodel.FilterHubViewModel
import kotlin.math.roundToInt

private enum class ActiveFilterSheet { Subjects, Systems, Status, Difficulty }

@Composable
internal fun FilterHubScreen(
    viewModel: FilterHubViewModel,
    onStartQuiz: () -> Unit,
    onHistorySelected: (QuizSessionRepository.QuizSession) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
    onDismissSnackbar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FilterHubContent(
        state = state,
        onStartQuiz = onStartQuiz,
        onHistorySelected = onHistorySelected,
        onLoggingToggle = { viewModel.setLoggingEnabled(it) },
        onSubmissionModeToggle = { viewModel.setSubmissionMode(it) },
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
        onUndoDelete = { viewModel.undoHistoryEntries(it) },
        onFetchSubjects = { viewModel.fetchSubjects() },
        onApplySelectedSubjects = { inc, excl -> viewModel.applySelectedSubjects(inc, excl) },
        onFetchSystems = { viewModel.fetchSystems() },
        onApplySelectedSystems = { inc, excl -> viewModel.applySelectedSystems(inc, excl) },
        onSelectStatusFilter = { viewModel.setStatusFilter(it) },
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
    onDeleteHistoryEntries: (Set<String>) -> Unit,
    onRenameHistoryEntry: (String, String) -> Unit,
    onCopyAllQids: (List<QuizSessionRepository.QuizSession>, (String) -> Unit) -> Unit,
    onUndoDelete: (List<QuizSessionRepository.QuizSession>) -> Unit,
    onFetchSubjects: () -> Unit,
    onApplySelectedSubjects: (Set<Long>, Set<Long>) -> Unit,
    onFetchSystems: () -> Unit,
    onApplySelectedSystems: (Set<Long>, Set<Long>) -> Unit,
    onSelectStatusFilter: (StatusFilter) -> Unit,
    onSelectDifficultyFilters: (Set<DifficultyTier>) -> Unit = {},
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
    onDismissSnackbar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val statusLabel = formatStatusFilterLabel(state.statusFilter)
    val difficultyLabel = formatDifficultyLabel(state.selectedDifficultyTiers)

    var activeSheet by rememberSaveable { mutableStateOf<ActiveFilterSheet?>(null) }
    var historySelectionMode by rememberSaveable { mutableStateOf(false) }
    val adaptive = LocalAdaptiveLayoutInfo.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    FilterPaneScaffold(
        selectedPane = state.activePane,
        onPaneSelected = onPaneSelected,
        showPaneToolbar = !adaptive.isExpandedWidth && (!historySelectionMode || state.activePane == FilterPane.Filters),
        modifier = modifier,
    ) {
        val renderFiltersPane: @Composable (Dp) -> Unit = { bottomContentPadding ->
            FilterScreen(
                databaseName = state.databaseName,
                subjectCount = state.selectedSubjectIds.size,
                excludedSubjectCount = state.excludedSubjectIds.size,
                systemCount = state.selectedSystemIds.size,
                excludedSystemCount = state.excludedSystemIds.size,
                statusFilter = state.statusFilter,
                statusLabel = statusLabel,
                selectedDifficultyTiers = state.selectedDifficultyTiers,
                difficultyLabel = difficultyLabel,
                isDifficultyAvailable = state.isDifficultyAvailable,
                previewCount = state.previewQuestionCount,
                isLoggingEnabled = state.isLoggingEnabled,
                onLoggingToggle = onLoggingToggle,
                submissionMode = state.submissionMode,
                onSubmissionModeToggle = onSubmissionModeToggle,
                bottomContentPadding = bottomContentPadding,
                onSelectSubjects = { activeSheet = ActiveFilterSheet.Subjects },
                onSelectSystems = { activeSheet = ActiveFilterSheet.Systems },
                onSelectStatus = { activeSheet = ActiveFilterSheet.Status },
                onSelectDifficulty = { activeSheet = ActiveFilterSheet.Difficulty },
                onStart = {
                    onDismissSnackbar()
                    onStartQuiz()
                },
                onClearFilters = onClearFilters,
            )
        }
        val renderHistoryPane: @Composable (Boolean) -> Unit = { hasFloatingToolbar ->
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
                hasFloatingToolbar = hasFloatingToolbar,
            )
        }

        if (adaptive.isExpandedWidth) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1.15f).fillMaxHeight()) {
                    renderFiltersPane(navBarBottom + 16.dp)
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(modifier = Modifier.weight(0.85f).fillMaxHeight()) {
                    renderHistoryPane(false)
                }
            }
        } else {
            val baseBottomPadding = if (adaptive.isCompactHeight) 64.dp else 88.dp
            val bottomContentPadding = baseBottomPadding + navBarBottom
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = state.activePane,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { (it * 0.20f * direction).roundToInt() } +
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                        (slideOutHorizontally(
                            animationSpec = exitSpatialSpec()
                        ) { (-it * 0.20f * direction).roundToInt() } +
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
                },
                label = "filter_pane_content",
            ) { activePane ->
                when (activePane) {
                    FilterPane.Filters -> renderFiltersPane(bottomContentPadding)
                    FilterPane.History -> renderHistoryPane(true)
                }
            }
        }
    }

    when (activeSheet) {
        ActiveFilterSheet.Subjects -> {
            SubjectFilterBottomSheet(
                resource = state.subjectsResource,
                selectedIds = state.selectedSubjectIds,
                excludedIds = state.excludedSubjectIds,
                onRetry = onFetchSubjects,
                onApply = { included, excluded ->
                    onApplySelectedSubjects(included, excluded)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null }
            )
        }
        ActiveFilterSheet.Systems -> {
            SystemFilterBottomSheet(
                resource = state.systemsResource,
                selectedIds = state.selectedSystemIds,
                excludedIds = state.excludedSystemIds,
                onRetry = onFetchSystems,
                onApply = { included, excluded ->
                    onApplySelectedSystems(included, excluded)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null }
            )
        }
        ActiveFilterSheet.Status -> {
            StatusFilterBottomSheet(
                current = state.statusFilter,
                markedCount = state.markedQuestionCount,
                onSelect = { filter ->
                    onSelectStatusFilter(filter)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null }
            )
        }
        ActiveFilterSheet.Difficulty -> {
            DifficultyFilterBottomSheet(
                selectedTiers = state.selectedDifficultyTiers,
                counts = state.difficultyCounts,
                onApply = { selected ->
                    onSelectDifficultyFilters(selected)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null }
            )
        }
        null -> Unit
    }
}
