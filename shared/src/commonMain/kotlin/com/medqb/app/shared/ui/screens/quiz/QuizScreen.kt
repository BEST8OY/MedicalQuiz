package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.richtext.HighlightableRichText
import com.medqb.app.shared.ui.richtext.RichTextScaleProvider
import com.medqb.app.shared.ui.screens.quiz.labs.LabsBottomSheet
import com.medqb.app.shared.ui.state.QuizUiState
import com.medqb.app.shared.utils.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuizScreen(
    state: QuizUiState,
    mediaHandler: MediaHandler,
    onAnswerSelected: (Long) -> Unit,
    onHighlightAdd: (HighlightSection, Int, Int, String, HighlightColor) -> Unit,
    onHighlightRemove: (Long) -> Unit,
    onHighlightColorChange: (Long, HighlightColor) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
    onRetry: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomClearance: Dp = 80.dp,
) {
    val fontScalePreference = state.fontScalePreference

    RichTextScaleProvider(proseScale = fontScalePreference ?: 1f) {
        QuestionContent(
            state = state,
            mediaHandler = mediaHandler,
            onAnswerSelected = onAnswerSelected,
            onHighlightAdd = onHighlightAdd,
            onHighlightRemove = onHighlightRemove,
            onHighlightColorChange = onHighlightColorChange,
            onShowSnackbar = onShowSnackbar,
            onRetry = onRetry,
            onNavigateHome = onNavigateHome,
            modifier = modifier,
            contentPadding = contentPadding,
            bottomClearance = bottomClearance,
        )
    }
}

@Composable
private fun QuestionContent(
    state: QuizUiState,
    mediaHandler: MediaHandler,
    onAnswerSelected: (Long) -> Unit,
    onHighlightAdd: (HighlightSection, Int, Int, String, HighlightColor) -> Unit,
    onHighlightRemove: (Long) -> Unit,
    onHighlightColorChange: (Long, HighlightColor) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit,
    onRetry: () -> Unit,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomClearance: Dp = 80.dp,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                ) {
                    QuizQuestionCard(
                        state = state,
                        mediaHandler = mediaHandler,
                        onAnswerSelected = onAnswerSelected,
                        onHighlightAdd = onHighlightAdd,
                        onHighlightRemove = onHighlightRemove,
                        onHighlightColorChange = onHighlightColorChange,
                        onShowSnackbar = onShowSnackbar,
                        onRetry = onRetry,
                        onNavigateHome = onNavigateHome,
                        bottomClearance = bottomClearance,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuizQuestionCard(
    state: QuizUiState,
    mediaHandler: MediaHandler,
    onAnswerSelected: (Long) -> Unit,
    onHighlightAdd: (HighlightSection, Int, Int, String, HighlightColor) -> Unit,
    onHighlightRemove: (Long) -> Unit,
    onHighlightColorChange: (Long, HighlightColor) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit,
    onRetry: () -> Unit,
    onNavigateHome: () -> Unit,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
) {
    val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val defaultSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    val question = state.currentQuestion
    val answers = state.currentAnswers
    val metadataSections = remember(question) { computeMetadataSections(question) }
    val uriHandler = LocalUriHandler.current
    val linkHandler: (String) -> Unit = remember(question?.id, mediaHandler) {
        { url ->
            val normalizedUrl = url.trim()
            if (normalizedUrl.isNotEmpty()) {
                if (!mediaHandler.handleMediaLink(normalizedUrl)) {
                    try {
                        uriHandler.openUri(normalizedUrl)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }
    val mediaClick: (String) -> Unit = remember(mediaHandler) { { ref -> mediaHandler.handleMediaLink(ref) } }

    LaunchedEffect(question?.id, question != null) {
        if (question != null) {
            val mediaFiles = withContext(Dispatchers.IO) {
                HtmlUtils.collectMediaFiles(question)
            }
            mediaHandler.updateMedia(question.id, mediaFiles)
        } else {
            mediaHandler.reset()
        }
    }

    if (question == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = state.isLoading,
                enter = fadeIn(animationSpec = defaultEffectsSpec),
                exit = fadeOut(animationSpec = defaultEffectsSpec)
            ) {
                LoadingIndicator()
            }
            AnimatedVisibility(
                visible = !state.isLoading && state.errorMessage != null,
                enter = fadeIn(animationSpec = defaultEffectsSpec),
                exit = fadeOut(animationSpec = defaultEffectsSpec)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "Unable to load question",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = state.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRetry) {
                        Text(text = "Retry")
                    }
                }
            }
            AnimatedVisibility(
                visible = !state.isLoading && state.errorMessage == null,
                enter = fadeIn(animationSpec = defaultEffectsSpec),
                exit = fadeOut(animationSpec = defaultEffectsSpec)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Quiz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "No question selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Select a question to begin or return to the question bank.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(onClick = onNavigateHome) {
                        Text(text = "Return to Bank")
                    }
                }
            }
        }
        return
    }

    val questionParts = remember(question.id, question.question) {
        HtmlUtils.extractQuestionHtmlParts(question.question)
    }
    val questionHtml = questionParts.contentHtml.ifBlank { "<p>Question content unavailable.</p>" }
    val hintHtml = questionParts.hintHtml
    val explanationHtml = remember(question.id, question.explanation) {
        HtmlUtils.sanitizeForRichText(question.explanation)
    }
    val sanitizedAnswers = remember(question.id, answers) {
        answers.associate { answer ->
            val normalized = HtmlUtils.normalizeAnswerHtml(answer.answerText)
            val sanitized = HtmlUtils.sanitizeForRichText(normalized)
            answer.answerId to sanitized
        }
    }
    val correctAnswerId = state.correctAnswerId
    val answerPercentages = remember(answers) {
        val total = answers.sumOf { it.correctPercentage ?: 0 }
        if (total == 0) {
            answers.associate { it.answerId to null }
        } else {
            val exactPercentages = answers.map { answer ->
                val count = answer.correctPercentage ?: 0
                answer.answerId to ((count * 100.0) / total)
            }
            val floored = exactPercentages.map { it.first to it.second.toInt() }
            val remainders = exactPercentages.mapIndexed { index, pair ->
                index to (pair.second - floored[index].second)
            }
            val deficit = 100 - floored.sumOf { it.second }
            val indicesToIncrement = remainders.sortedByDescending { it.second }.take(deficit).map { it.first }

            floored.mapIndexed { index, pair ->
                pair.first to (pair.second + if (index in indicesToIncrement) 1 else 0)
            }.toMap()
        }
    }
    val hasKeyInfo = remember(question.id, questionHtml) {
        HtmlUtils.hasKeyInfo(questionHtml)
    }
    var keyInfoToggled by rememberSaveable(question.id) { mutableStateOf<Boolean?>(null) }
    val isKeyInfoActive = keyInfoToggled ?: state.answerSubmitted
    var attendingTipVisible by rememberSaveable(question.id) { mutableStateOf(false) }
    var showLabsSheet by rememberSaveable { mutableStateOf(false) }

    val scrollState = key(state.currentQuestionIndex) { rememberScrollState() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = bottomClearance + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 1.dp
            )
        ) {
            HighlightableRichText(
                html = questionHtml,
                section = HighlightSection.QUESTION,
                highlights = state.questionHighlights,
                showSelectedHighlight = isKeyInfoActive,
                onHighlightAdd = onHighlightAdd,
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onLinkClick = linkHandler,
                onMediaClick = mediaClick,
                onShowSnackbar = onShowSnackbar,
                modifier = Modifier.padding(16.dp)
            )
        }

        QuizStudySection(
            questionId = question.id,
            hasKeyInfo = hasKeyInfo,
            isKeyInfoActive = isKeyInfoActive,
            onToggleKeyInfo = { keyInfoToggled = !isKeyInfoActive },
            hasAttendingTip = hintHtml != null,
            isAttendingTipActive = attendingTipVisible,
            onToggleAttendingTip = { attendingTipVisible = !attendingTipVisible },
            isLabsActive = showLabsSheet,
            onOpenLabs = { showLabsSheet = true },
            hintHtml = hintHtml,
            linkHandler = linkHandler,
            mediaClick = mediaClick,
        )

        AnswerOptions(
            answers = answers,
            sanitizedAnswers = sanitizedAnswers,
            selectedAnswerId = state.selectedAnswerId,
            correctAnswerId = correctAnswerId,
            answerSubmitted = state.answerSubmitted,
            answerPercentages = answerPercentages,
            onAnswerSelected = onAnswerSelected,
            onLinkClick = linkHandler,
            onMediaClick = mediaClick
        )

        AnimatedVisibility(
            visible = state.answerSubmitted && explanationHtml.isNotBlank(),
            enter = fadeIn(
                animationSpec = defaultEffectsSpec,
            ) + expandVertically(
                animationSpec = defaultSpatialSpec,
            ),
            exit = ExitTransition.None,
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 1.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Explanation",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    HighlightableRichText(
                        html = explanationHtml,
                        section = HighlightSection.EXPLANATION,
                        highlights = state.explanationHighlights,
                        showSelectedHighlight = state.answerSubmitted,
                        onHighlightAdd = onHighlightAdd,
                        onHighlightRemove = onHighlightRemove,
                        onHighlightColorChange = onHighlightColorChange,
                        onLinkClick = linkHandler,
                        onMediaClick = mediaClick,
                        onShowSnackbar = onShowSnackbar
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.showMetadata && state.answerSubmitted && metadataSections.isNotEmpty(),
            enter = fadeIn(
                animationSpec = defaultEffectsSpec,
            ) + expandVertically(
                animationSpec = defaultSpatialSpec,
            ),
            exit = ExitTransition.None,
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                QuestionMetadataCard(sections = metadataSections)
            }
        }

        AnimatedVisibility(
            visible = state.answerSubmitted && state.currentPerformance != null && state.isLoggingEnabled,
            enter = fadeIn(
                animationSpec = defaultEffectsSpec,
            ) + expandVertically(
                animationSpec = defaultSpatialSpec,
            ),
            exit = ExitTransition.None,
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                PerformanceCard(performance = state.currentPerformance)
            }
        }
    }

    if (showLabsSheet) {
        LabsBottomSheet(onDismiss = { showLabsSheet = false })
    }
}
