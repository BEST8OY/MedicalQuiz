package com.medqb.app.shared.ui.screens.quiz

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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.LocalActiveSharedElementKey
import com.medqb.app.shared.ui.dialogs.JumpToDialog
import com.medqb.app.shared.ui.screens.media.PlatformBackHandler
import com.medqb.app.shared.viewmodel.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizRoot(
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    onNavigateBack: () -> Unit,
    onOpenSettingsScreen: () -> Unit,
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
    var showJumpToDialog by rememberSaveable { mutableStateOf(false) }
    val isOverlayVisible = showJumpToDialog

    // In pre-quiz mode, back button should navigate back to filter screen.
    // PlatformBackHandler is used here instead of NavDisplay's onBack because:
    // 1. This is destination-specific logic (only applies to Quiz screen)
    // 2. We need to check both quiz mode AND dialog overlay state
    // 3. Navigation 3 pattern: use PlatformBackHandler for complex destination-specific back handling
    PlatformBackHandler(
        enabled = !isQuizMode && !isOverlayVisible,
        onBack = onNavigateBack
    )

    CompositionLocalProvider(LocalActiveSharedElementKey provides mediaHandler.activeSharedElementKey) {
    Box(modifier = Modifier.fillMaxSize()) {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopBar(
                    title = title,
                    onResetLogClick = { viewModel.clearCurrentQuestionLog() },
                    onSettingsClick = onOpenSettingsScreen
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
                onOpenSettings = onOpenSettingsScreen,
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
                showSubmitButton = !state.answerSubmitted && state.submissionMode == SubmissionMode.MANUAL,
                canSubmit = state.selectedAnswerId != null,
            ),
            onPrevious = { viewModel.loadPrevious() },
            onNext = { viewModel.loadNext() },
            onJumpTo = { showJumpToDialog = true },
            onSubmit = { viewModel.submitAnswer(timeTaken = 0L) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + 16.dp)
        )
    }

    // Dialogs - rendered as overlays
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

    } // CompositionLocalProvider
}
