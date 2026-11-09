package com.medqb.app.shared.ui.screens.filter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
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
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.dialogs.DifficultyFilterBottomSheet
import com.medqb.app.shared.ui.dialogs.StatusFilterBottomSheet
import com.medqb.app.shared.ui.dialogs.SubjectFilterBottomSheet
import com.medqb.app.shared.ui.dialogs.SystemFilterBottomSheet
import com.medqb.app.shared.ui.screens.history.HistoryPane
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
    onDeleteHistoryEntries: suspend (Set<String>) -> Unit,
    onRenameHistoryEntry: (String, String) -> Unit,
    onCopyAllQids: (List<QuizSessionRepository.QuizSession>, (String) -> Unit) -> Unit,
    onUndoDelete: suspend (QuizSessionRepository.QuizSession) -> Unit,
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

    var showSubjectSheet by rememberSaveable { mutableStateOf(false) }
    var showSystemSheet by rememberSaveable { mutableStateOf(false) }
    var showStatusSheet by rememberSaveable { mutableStateOf(false) }
    var showDifficultySheet by rememberSaveable { mutableStateOf(false) }

    var historySelectionMode by rememberSaveable { mutableStateOf(false) }

    FilterPaneScaffold(
        selectedPane = state.activePane,
        onPaneSelected = onPaneSelected,
        showPaneToolbar = !historySelectionMode || state.activePane == FilterPane.Filters,
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val baseBottomPadding = if (screenHeight < 400.dp) 64.dp else 88.dp
            val bottomContentPadding = baseBottomPadding + navBarBottom
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = state.activePane,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    ((slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { (it * 0.20f * direction).roundToInt() } +
                        fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                        (slideOutHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { (-it * 0.20f * direction).roundToInt() } +
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec()))).apply {
                        targetContentZIndex = 0f
                    }
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
                            onSelectSubjects = { showSubjectSheet = true },
                            onSelectSystems = { showSystemSheet = true },
                            onSelectStatus = { showStatusSheet = true },
                            onSelectDifficulty = { showDifficultySheet = true },
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

    if (showSubjectSheet) {
        SubjectFilterBottomSheet(
            resource = state.subjectsResource,
            selectedIds = state.selectedSubjectIds,
            excludedIds = state.excludedSubjectIds,
            onRetry = onFetchSubjects,
            onApply = { included, excluded ->
                onApplySelectedSubjects(included, excluded)
                showSubjectSheet = false
            },
            onDismiss = { showSubjectSheet = false }
        )
    }

    if (showSystemSheet) {
        SystemFilterBottomSheet(
            resource = state.systemsResource,
            selectedIds = state.selectedSystemIds,
            excludedIds = state.excludedSystemIds,
            onRetry = onFetchSystems,
            onApply = { included, excluded ->
                onApplySelectedSystems(included, excluded)
                showSystemSheet = false
            },
            onDismiss = { showSystemSheet = false }
        )
    }

    if (showStatusSheet) {
        StatusFilterBottomSheet(
            current = state.statusFilter,
            onSelect = { filter ->
                onSelectStatusFilter(filter)
                showStatusSheet = false
            },
            onDismiss = { showStatusSheet = false }
        )
    }

    if (showDifficultySheet) {
        DifficultyFilterBottomSheet(
            selectedTiers = state.selectedDifficultyTiers,
            counts = state.difficultyCounts,
            onApply = { selected ->
                onSelectDifficultyFilters(selected)
                showDifficultySheet = false
            },
            onDismiss = { showDifficultySheet = false }
        )
    }
}

private fun formatStatusFilterLabel(filter: StatusFilter): String {
    if (filter.isAll) return "All Questions"

    if (filter.statuses.size == 1) {
        val single = filter.statuses.first()
        return when (single) {
            QuestionStatus.NOT_YET_ANSWERED -> "Not yet answered"
            QuestionStatus.ANSWERED_INCORRECTLY -> if (filter.scope == StatusScope.LATEST_ATTEMPT) "Last incorrect" else "Ever incorrect"
            QuestionStatus.ANSWERED_CORRECTLY -> if (filter.scope == StatusScope.LATEST_ATTEMPT) "Last correct" else "Ever correct"
        }
    }

    if (filter.statuses.size == 2 &&
        QuestionStatus.NOT_YET_ANSWERED in filter.statuses &&
        QuestionStatus.ANSWERED_INCORRECTLY in filter.statuses
    ) {
        return if (filter.scope == StatusScope.LATEST_ATTEMPT) {
            "Unanswered & Incorrect"
        } else {
            "Unanswered & Ever incorrect"
        }
    }

    return "${filter.statuses.size} statuses (${filter.scope.shortLabel})"
}

