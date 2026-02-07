package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.ui.media.MediaHandler
import com.medicalquiz.app.shared.ui.dialogs.ErrorDialog
import com.medicalquiz.app.shared.ui.dialogs.JumpToDialog
import com.medicalquiz.app.shared.ui.dialogs.PerformanceFilterDialog
import com.medicalquiz.app.shared.ui.dialogs.ResetConfirmationDialog
import com.medicalquiz.app.shared.ui.dialogs.SettingsDialogWithViewModel
import com.medicalquiz.app.shared.ui.dialogs.SubjectFilterDialog
import com.medicalquiz.app.shared.ui.dialogs.SystemFilterDialog
import com.medicalquiz.app.shared.ui.screens.media.PlatformBackHandler
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import com.medicalquiz.app.shared.viewmodel.UiEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizRoot(
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    onNavigateBack: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title by viewModel.toolbarTitle.collectAsStateWithLifecycle()
    val isQuizMode = state.questionIds.isNotEmpty() && state.currentQuestion != null

    val uriHandler = LocalUriHandler.current
    val linkHandler: (String) -> Unit = remember(mediaHandler, uriHandler) {
        { url ->
            val normalizedUrl = url.trim()
            if (normalizedUrl.isEmpty()) return@remember
            if (!mediaHandler.handleMediaLink(normalizedUrl)) {
                try {
                    uriHandler.openUri(normalizedUrl)
                } catch (_: Exception) {
                    // Ignore
                }
            }
        }
    }

    // Dialog states - these are overlays within the quiz screen
    var showPerformanceDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showJumpToDialog by rememberSaveable { mutableStateOf(false) }
    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showResetLogsConfirmation by rememberSaveable { mutableStateOf(false) }
    var errorDialog by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    val isOverlayVisible = showPerformanceDialog ||
        showSettingsDialog ||
        showJumpToDialog ||
        showSubjectDialog ||
        showSystemDialog ||
        showResetLogsConfirmation ||
        errorDialog != null

    // In pre-quiz mode, back button should navigate back to filter screen.
    // PlatformBackHandler is used here instead of NavDisplay's onBack because:
    // 1. This is destination-specific logic (only applies to Quiz screen)
    // 2. We need to check both quiz mode AND dialog overlay state
    // 3. Navigation 3 pattern: use PlatformBackHandler for complex destination-specific back handling
    PlatformBackHandler(
        enabled = !isQuizMode && !isOverlayVisible,
        onBack = onNavigateBack
    )

    // Event handling from ViewModel
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.OpenPerformanceDialog -> showPerformanceDialog = true
                is UiEvent.ShowErrorDialog -> errorDialog = event.title to event.message
                is UiEvent.ShowResetLogsConfirmation -> showResetLogsConfirmation = true
                // OpenHtmlFile and OpenMedia are now handled by NavDisplay in App.kt
                else -> Unit
            }
        }
    }

    LaunchedEffect(showSubjectDialog) {
        if (showSubjectDialog) viewModel.fetchSubjects()
    }

    LaunchedEffect(showSystemDialog, state.selectedSubjectIds) {
        if (showSystemDialog) {
            val subjects = state.selectedSubjectIds.takeIf { it.isNotEmpty() }?.toList()
            viewModel.fetchSystemsForSubjects(subjects)
        }
    }

    // Quiz screen with navigation drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            NavigationDrawer(
                subjectCount = state.selectedSubjectIds.size,
                systemCount = state.selectedSystemIds.size,
                performanceFilter = state.performanceFilter,
                onSubjectFilter = {
                    showSubjectDialog = true
                    scope.launch { drawerState.close() }
                },
                onSystemFilter = {
                    showSystemDialog = true
                    scope.launch { drawerState.close() }
                },
                onPerformanceFilter = {
                    viewModel.openPerformanceDialog()
                    scope.launch { drawerState.close() }
                },
                onClearFilters = {
                    viewModel.applySelectedSubjects(emptySet())
                    viewModel.applySelectedSystems(emptySet())
                    viewModel.setPerformanceFilter(PerformanceFilter.ALL, loadQuestions = true)
                    scope.launch { drawerState.close() }
                },
                onSettings = {
                    showSettingsDialog = true
                    scope.launch { drawerState.close() }
                },
                onChangeDatabase = {
                    viewModel.navigateToDatabaseSelection()
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopBar(
                    title = title,
                    questionIndex = state.currentQuestionIndex,
                    totalQuestions = state.totalQuestions,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onJumpClick = { showJumpToDialog = true },
                    onResetLogClick = { viewModel.clearCurrentQuestionLog() },
                    onSettingsClick = { showSettingsDialog = true }
                )
            },
            bottomBar = {
                QuizFloatingToolbar(
                    uiState = QuizBottomToolbarUiState(
                        currentQuestionIndex = state.currentQuestionIndex,
                        totalQuestions = state.totalQuestions,
                        hasPreviousQuestion = state.hasPreviousQuestion,
                        hasNextQuestion = state.hasNextQuestion,
                    ),
                    onPrevious = { viewModel.loadPrevious() },
                    onNext = { viewModel.loadNext() },
                    onJumpTo = { showJumpToDialog = true },
                )
            }
        ) { padding ->
            QuizScreen(
                viewModel = viewModel,
                mediaHandler = mediaHandler,
                onPrevious = { viewModel.loadPrevious() },
                onNext = { viewModel.loadNext() },
                onJumpTo = { showJumpToDialog = true },
                onOpenSettings = { showSettingsDialog = true },
                contentPadding = padding
            )
        }
    }

    // Dialogs - rendered as overlays
    if (showPerformanceDialog) {
        PerformanceFilterDialog(
            current = state.performanceFilter,
            onSelect = { filter ->
                viewModel.setPerformanceFilter(filter, loadQuestions = isQuizMode)
                showPerformanceDialog = false
            },
            onDismiss = { showPerformanceDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialogWithViewModel(
            isVisible = true,
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onResetLogsRequested = { showResetLogsConfirmation = true }
        )
    }

    if (showJumpToDialog) {
        JumpToDialog(
            isVisible = true,
            totalQuestions = state.questionIds.size,
            currentIndex = state.currentQuestionIndex,
            onJumpTo = { index ->
                viewModel.loadQuestion(index)
                showJumpToDialog = false
            },
            onDismiss = { showJumpToDialog = false }
        )
    }

    if (showSubjectDialog) {
        SubjectFilterDialog(
            isVisible = true,
            resource = state.subjectsResource,
            selectedIds = state.selectedSubjectIds,
            onRetry = { viewModel.fetchSubjects() },
            onApply = { selected ->
                viewModel.applySelectedSubjects(selected, loadQuestions = isQuizMode)
                showSubjectDialog = false
            },
            onClear = {
                viewModel.applySelectedSubjects(emptySet(), loadQuestions = isQuizMode)
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
                viewModel.applySelectedSystems(selected, loadQuestions = isQuizMode)
                showSystemDialog = false
            },
            onClear = {
                viewModel.applySelectedSystems(emptySet(), loadQuestions = isQuizMode)
                showSystemDialog = false
            },
            onDismiss = { showSystemDialog = false }
        )
    }

    if (showResetLogsConfirmation) {
        ResetConfirmationDialog(
            isVisible = true,
            onConfirm = {
                showResetLogsConfirmation = false
                viewModel.clearLogsFromDb()
            },
            onDismiss = { showResetLogsConfirmation = false }
        )
    }

    errorDialog?.let { (title, message) ->
        ErrorDialog(
            errorDialog = title to message,
            onDismiss = { errorDialog = null }
        )
    }
}
