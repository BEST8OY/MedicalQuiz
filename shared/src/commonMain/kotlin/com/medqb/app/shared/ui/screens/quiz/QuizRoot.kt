package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.ui.LocalActiveSharedElementKey
import com.medqb.app.shared.ui.components.SnackbarBottomClearanceEffect
import com.medqb.app.shared.ui.dialogs.JumpToDialog
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.viewmodel.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizRoot(
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    onNavigateBack: () -> Unit = {},
    onOpenSettingsScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = state.toolbarTitle

    // Dialog states - these are overlays within the quiz screen
    var showJumpToDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.questionIds.isEmpty()) {
        if (state.questionIds.isEmpty()) showJumpToDialog = false
    }

    SnackbarBottomClearanceEffect(
        clearance = 84.dp,
        active = state.questionIds.isNotEmpty(),
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    CompositionLocalProvider(LocalActiveSharedElementKey provides mediaHandler.activeSharedElementKey) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (showJumpToDialog || event.type != KeyEventType.KeyDown ||
                        event.isCtrlPressed || event.isMetaPressed || event.isAltPressed
                    ) {
                        return@onKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionLeft -> if (state.hasPreviousQuestion) {
                            viewModel.loadPrevious()
                            true
                        } else {
                            false
                        }
                        Key.DirectionRight -> if (state.hasNextQuestion) {
                            viewModel.loadNext()
                            true
                        } else {
                            false
                        }
                        Key.Enter, Key.NumPadEnter -> if (
                            !state.answerSubmitted &&
                            state.submissionMode == SubmissionMode.MANUAL &&
                            state.selectedAnswerId != null
                        ) {
                            viewModel.submitAnswer(timeTaken = 0L)
                            true
                        } else {
                            false
                        }
                        Key.F -> if (state.currentQuestion != null) {
                            viewModel.toggleMark()
                            true
                        } else {
                            false
                        }
                        Key.J -> if (state.questionIds.isNotEmpty()) {
                            showJumpToDialog = true
                            true
                        } else {
                            false
                        }
                        else -> {
                            val answerIndex = when (event.key) {
                                Key.One -> 0
                                Key.Two -> 1
                                Key.Three -> 2
                                Key.Four -> 3
                                Key.Five -> 4
                                Key.Six -> 5
                                Key.Seven -> 6
                                Key.Eight -> 7
                                Key.Nine -> 8
                                else -> -1
                            }
                            if (!state.answerSubmitted && answerIndex in state.currentAnswers.indices) {
                                val answerId = state.currentAnswers[answerIndex].answerId
                                viewModel.onAnswerSelected(answerId)
                                if (state.submissionMode == SubmissionMode.INSTANT) {
                                    viewModel.submitAnswer(timeTaken = 0L)
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
        ) {
            val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopBar(
                        title = title,
                        isMarked = state.isCurrentQuestionMarked,
                        canMark = state.currentQuestion != null,
                        onToggleMark = { viewModel.toggleMark() },
                        onNavigateBack = onNavigateBack,
                        onResetLogClick = { viewModel.clearCurrentQuestionLog() },
                        onSettingsClick = onOpenSettingsScreen,
                    )
                },
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
            ) { padding ->
                QuizScreen(
                    state = state,
                    mediaHandler = mediaHandler,
                    onAnswerSelected = { answerId ->
                        if (!state.answerSubmitted) {
                            viewModel.onAnswerSelected(answerId)
                            if (state.submissionMode == SubmissionMode.INSTANT) {
                                viewModel.submitAnswer(timeTaken = 0L)
                            }
                        }
                    },
                    onHighlightAdd = viewModel::addHighlight,
                    onHighlightRemove = viewModel::removeHighlight,
                    onHighlightColorChange = viewModel::changeHighlightColor,
                    onShowSnackbar = { viewModel.emitSnackbar(it) },
                    onRetry = { viewModel.retry() },
                    onNavigateHome = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    bottomClearance = bottomPadding + 80.dp
                )
            }

            // Floating toolbar - overlays content
            if (state.questionIds.isNotEmpty()) {
                QuizFloatingToolbar(
                    uiState = QuizBottomToolbarUiState(
                        currentQuestionIndex = state.currentQuestionIndex,
                        totalQuestions = state.totalQuestions,
                        hasPreviousQuestion = state.hasPreviousQuestion,
                        hasNextQuestion = state.hasNextQuestion,
                        showSubmitButton = !state.answerSubmitted && state.submissionMode == SubmissionMode.MANUAL,
                        canSubmit = state.selectedAnswerId != null,
                        isCurrentQuestionMarked = state.isCurrentQuestionMarked,
                    ),
                    onPrevious = { viewModel.loadPrevious() },
                    onNext = { viewModel.loadNext() },
                    onJumpTo = { showJumpToDialog = true },
                    onSubmit = { viewModel.submitAnswer(timeTaken = 0L) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding + 16.dp)
                        .zIndex(1f)
                )
            }
        }

        // Dialogs - rendered as overlays
        if (showJumpToDialog && state.questionIds.isNotEmpty()) {
            JumpToDialog(
                totalQuestions = state.totalQuestions,
                currentIndex = state.currentQuestionIndex,
                onJumpTo = { index ->
                    viewModel.loadQuestion(index)
                    showJumpToDialog = false
                },
                onDismiss = { showJumpToDialog = false }
            )
        }
    }
}
