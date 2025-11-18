package com.medicalquiz.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.data.database.PerformanceFilter
import com.medicalquiz.app.data.database.QuestionPerformance
import com.medicalquiz.app.utils.WebViewComposable
import com.medicalquiz.app.utils.WebViewState
import com.medicalquiz.app.viewmodel.UiEvent
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizScreen(
    viewModel: com.medicalquiz.app.viewmodel.QuizViewModel,
    webViewStateFlow: MutableStateFlow<WebViewState>,
    mediaHandler: MediaHandler,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowFilterSubject: () -> Unit,
    onShowFilterSystem: () -> Unit,
    onSelectPerformance: () -> Unit,
    onStart: () -> Unit,
    onClearFilters: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val performanceLabel = remember(state.performanceFilter) { formatPerformanceLabel(state.performanceFilter) }
    val hasQuestions = state.questionIds.isNotEmpty() && state.currentQuestion != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Crossfade(targetState = hasQuestions, modifier = Modifier.fillMaxSize(), label = "quiz-body") { available ->
            if (available) {
                QuestionContent(
                    state = state,
                    viewModel = viewModel,
                    webViewStateFlow = webViewStateFlow,
                    mediaHandler = mediaHandler,
                    performanceLabel = performanceLabel,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onJumpTo = onJumpTo,
                    onOpenSettings = onOpenSettings,
                    onShowFilterSubject = onShowFilterSubject,
                    onShowFilterSystem = onShowFilterSystem,
                    onSelectPerformance = onSelectPerformance
                )
            } else {
                EmptyFiltersState(
                    subjectCount = state.selectedSubjectIds.size,
                    systemCount = state.selectedSystemIds.size,
                    previewCount = state.previewQuestionCount,
                    performanceLabel = performanceLabel,
                    onSelectSubjects = onShowFilterSubject,
                    onSelectSystems = onShowFilterSystem,
                    onSelectPerformance = onSelectPerformance,
                    onStart = onStart,
                    onClearFilters = onClearFilters
                )
            }
        }
    }

    LaunchedEffect(viewModel, mediaHandler) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.OpenMedia -> mediaHandler.handleMediaLink(event.url)
                else -> Unit
            }
        }
    }
}

@Composable
private fun QuestionContent(
    state: QuizState,
    viewModel: com.medicalquiz.app.viewmodel.QuizViewModel,
    webViewStateFlow: MutableStateFlow<WebViewState>,
    mediaHandler: MediaHandler,
    performanceLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowFilterSubject: () -> Unit,
    onShowFilterSystem: () -> Unit,
    onSelectPerformance: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuestionHeader(
            state = state,
            performanceLabel = performanceLabel,
            onJumpTo = onJumpTo,
            onOpenSettings = onOpenSettings,
            onShowFilterSubject = onShowFilterSubject,
            onShowFilterSystem = onShowFilterSystem,
            onSelectPerformance = onSelectPerformance
        )

        QuestionStepper(
            hasPrevious = state.hasPreviousQuestion,
            hasNext = state.hasNextQuestion,
            onPrevious = onPrevious,
            onNext = onNext
        )

        AnimatedVisibility(
            visible = state.answerSubmitted || state.currentPerformance != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PerformanceSummaryCard(performance = state.currentPerformance, answerSubmitted = state.answerSubmitted)
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(24.dp)
        ) {
            WebViewComposable(
                stateFlow = webViewStateFlow,
                onAnswerSelected = { answerId ->
                    viewModel.onAnswerSelected(answerId)
                    viewModel.submitAnswer(timeTaken = 0L)
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

@Composable
private fun QuestionHeader(
    state: QuizState,
    performanceLabel: String,
    onJumpTo: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowFilterSubject: () -> Unit,
    onShowFilterSystem: () -> Unit,
    onSelectPerformance: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.questionNumberText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.currentQuestion?.title?.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onJumpTo) {
                        Text("Jump")
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Open settings")
                    }
                }
            }

            MetadataChips(
                state = state,
                performanceLabel = performanceLabel,
                onShowFilterSubject = onShowFilterSubject,
                onShowFilterSystem = onShowFilterSystem,
                onSelectPerformance = onSelectPerformance
            )
        }
    }
}

@Composable
private fun MetadataChips(
    state: QuizState,
    performanceLabel: String,
    onShowFilterSubject: () -> Unit,
    onShowFilterSystem: () -> Unit,
    onSelectPerformance: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.currentQuestion?.subName?.takeIf { it.isNotBlank() }?.let { subject ->
            AssistChip(onClick = onShowFilterSubject, label = { Text("Subject • $subject") }, leadingIcon = {
                Icon(imageVector = Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            })
        }

        state.currentQuestion?.sysName?.takeIf { it.isNotBlank() }?.let { system ->
            AssistChip(onClick = onShowFilterSystem, label = { Text("System • $system") }, leadingIcon = {
                Icon(imageVector = Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            })
        }

        AssistChip(
            onClick = onSelectPerformance,
            label = { Text("Performance • $performanceLabel") },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )
    }
}

@Composable
private fun QuestionStepper(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(onClick = onPrevious, enabled = hasPrevious, modifier = Modifier.weight(1f)) {
            Text("Previous")
        }
        FilledTonalButton(onClick = onNext, enabled = hasNext, modifier = Modifier.weight(1f)) {
            Text("Next")
        }
    }
}

@Composable
private fun PerformanceSummaryCard(
    performance: QuestionPerformance?,
    answerSubmitted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Your performance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            performance?.let {
                Text(
                    text = "Attempts: ${it.attempts}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Last attempt: ${if (it.lastCorrect) "Correct" else "Incorrect"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Ever correct: ${if (it.everCorrect) "Yes" else "No"}")
                    Text("Ever incorrect: ${if (it.everIncorrect) "Yes" else "No"}")
                }
            }

            if (performance == null && answerSubmitted) {
                Text(
                    text = "Answer logged locally.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun EmptyFiltersState(
    subjectCount: Int,
    systemCount: Int,
    previewCount: Int,
    performanceLabel: String,
    onSelectSubjects: () -> Unit,
    onSelectSystems: () -> Unit,
    onSelectPerformance: () -> Unit,
    onStart: () -> Unit,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Set a few filters to build your quiz",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (previewCount > 0) "$previewCount questions ready" else "Preview updates as you pick filters",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = onSelectSubjects, label = { Text("Subjects • $subjectCount") })
            AssistChip(onClick = onSelectSystems, label = { Text("Systems • $systemCount") })
            AssistChip(onClick = onSelectPerformance, label = { Text("Performance • $performanceLabel") })
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStart,
            enabled = previewCount > 0,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Start quiz")
        }

        OutlinedButton(
            onClick = onClearFilters,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Clear filters")
        }

        TextButton(onClick = onSelectPerformance, modifier = Modifier.padding(top = 8.dp)) {
            Text("Adjust performance filter")
        }
    }
}

private fun formatPerformanceLabel(filter: PerformanceFilter): String = when (filter) {
    PerformanceFilter.ALL -> "All Questions"
    PerformanceFilter.UNANSWERED -> "Not Attempted"
    PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
    PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
    PerformanceFilter.EVER_CORRECT -> "Ever Correct"
    PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
}
