package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.shared.ui.media.MediaHandler
import com.medicalquiz.app.shared.ui.dialogs.ErrorDialog
import com.medicalquiz.app.shared.ui.dialogs.JumpToDialog
import com.medicalquiz.app.shared.ui.dialogs.ResetConfirmationDialog
import com.medicalquiz.app.shared.ui.dialogs.SettingsDialogWithViewModel
import com.medicalquiz.app.shared.ui.screens.media.PlatformBackHandler
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import com.medicalquiz.app.shared.viewmodel.UiEvent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizRoot(
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    onNavigateBack: () -> Unit,
) {
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
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showJumpToDialog by rememberSaveable { mutableStateOf(false) }
    var showResetLogsConfirmation by rememberSaveable { mutableStateOf(false) }
    var errorDialog by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    val isOverlayVisible = showSettingsDialog ||
        showJumpToDialog ||
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
                is UiEvent.ShowErrorDialog -> errorDialog = event.title to event.message
                is UiEvent.ShowResetLogsConfirmation -> showResetLogsConfirmation = true
                // OpenHtmlFile and OpenMedia are now handled by NavDisplay in App.kt
                else -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopBar(
                    title = title,
                    onResetLogClick = { viewModel.clearCurrentQuestionLog() },
                    onSettingsClick = { showSettingsDialog = true }
                )
            },
            contentWindowInsets = WindowInsets.statusBars
        ) { padding ->
            QuizScreen(
                viewModel = viewModel,
                mediaHandler = mediaHandler,
                onPrevious = { viewModel.loadPrevious() },
                onNext = { viewModel.loadNext() },
                onJumpTo = { showJumpToDialog = true },
                onOpenSettings = { showSettingsDialog = true },
                contentPadding = padding,
                bottomClearance = bottomPadding + 80.dp
            )
        }

        // Floating toolbar - overlays content
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + 16.dp)
        )
    }

    // Dialogs - rendered as overlays
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
