package com.medicalquiz.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.utils.WebViewState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A Compose-based top-level quiz screen that contains the title, metadata,
 * the WebView composable for rendering questions and a small bottom bar
 * with navigation controls.
 *
 * This file is intended as an incremental step towards a full Compose
 * migration for `QuizActivity`. It keeps the logic in the activity and viewModel
 * but displays the UI as Compose. Event handlers are passed in as lambdas.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: com.medicalquiz.app.viewmodel.QuizViewModel,
    webViewStateFlow: MutableStateFlow<WebViewState>,
    mediaHandler: com.medicalquiz.app.ui.MediaHandler,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowFilterSubject: () -> Unit,
    onShowFilterSystem: () -> Unit,
    onSelectPerformance: () -> Unit,
    onStart: () -> Unit,
    onClearFilters: () -> Unit,
    // Padding coming from Scaffold
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    

    
    // Debug logging for state changes
    LaunchedEffect(state.questionIds) {
        android.util.Log.d("QuizScreen", "QuestionIds changed: count=${state.questionIds.size}, showingFilters=${state.questionIds.isEmpty()}")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
        // Metadata section (fixed height, only takes what it needs)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            // Show metadata and stats after answer is submitted
            if (state.answerSubmitted) {
                val question = state.currentQuestion
                if (question != null) {
                    // Question metadata
                    Text(
                        text = "ID: ${question.id} | Subject: ${question.subName} | System: ${question.sysName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                
                // Performance stats
                state.currentPerformance?.let { perf ->
                    val lastResultText = if (perf.lastCorrect) "Correct" else "Incorrect"
                    val correctCount = if (perf.everCorrect) 1 else 0
                    val incorrectCount = if (perf.everIncorrect) 1 else 0
                    Text(
                        text = "Attempted: ${perf.attempts} | Last: $lastResultText | Correct: $correctCount | Incorrect: $incorrectCount",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Filter panel — shows whenever questions list is empty
        if (state.questionIds.isEmpty()) {
            android.util.Log.d("QuizScreen", "Rendering StartFiltersPanel with ${state.selectedSubjectIds.size} subjects selected")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                StartFiltersPanel(
                    subjectCount = state.selectedSubjectIds.size,
                    systemCount = state.selectedSystemIds.size,
                    performanceLabel = mapPerformanceLabel(state.performanceFilter),
                    previewCount = state.previewQuestionCount,
                    onSelectSubjects = onShowFilterSubject,
                    onSelectSystems = onShowFilterSystem,
                    onSelectPerformance = onSelectPerformance,
                    onClear = onClearFilters,
                    onStart = onStart
                )
            }
        } else {
            // Render question WebView with weight to fill remaining space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                com.medicalquiz.app.utils.WebViewComposable(
                    stateFlow = webViewStateFlow,
                    onAnswerSelected = { answerId ->
                        viewModel.onAnswerSelected(answerId)
                        val timeTaken = System.currentTimeMillis() - (0L) // Activity manages startTime
                        viewModel.submitAnswer(timeTaken)
                    },
                    onOpenMedia = { mediaRef ->
                        val ref = mediaRef.takeIf { it.isNotBlank() } ?: return@WebViewComposable
                        mediaHandler.handleMediaLink(ref)
                    },
                    mediaHandler = mediaHandler
                )
            }
        }
        }
    }

    // Forward UiEvent.OpenMedia / Toasts to the host via the viewModel's shared flow.
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is com.medicalquiz.app.viewmodel.UiEvent.OpenMedia -> {
                    mediaHandler.handleMediaLink(event.url)
                }
                is com.medicalquiz.app.viewmodel.UiEvent.ShowToast -> {
                    // The activity collects and shows toasts; we ignore here
                }
                else -> {}
            }
        }
    }
}

private fun mapPerformanceLabel(filter: com.medicalquiz.app.data.database.PerformanceFilter): String = when (filter) {
    com.medicalquiz.app.data.database.PerformanceFilter.ALL -> "All Questions"
    com.medicalquiz.app.data.database.PerformanceFilter.UNANSWERED -> "Not Attempted"
    com.medicalquiz.app.data.database.PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
    com.medicalquiz.app.data.database.PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
    com.medicalquiz.app.data.database.PerformanceFilter.EVER_CORRECT -> "Ever Correct"
    com.medicalquiz.app.data.database.PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
}
