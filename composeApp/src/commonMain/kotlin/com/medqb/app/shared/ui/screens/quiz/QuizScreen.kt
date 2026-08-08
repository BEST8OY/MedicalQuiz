package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.SubmissionMode
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.richtext.HighlightableRichText
import com.medqb.app.shared.ui.richtext.RichTextScaleProvider
import com.medqb.app.shared.ui.state.QuizUiState
import com.medqb.app.shared.ui.theme.ScreenLayout
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.utils.HtmlUtils
import com.medqb.app.shared.viewmodel.QuizViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuizScreen(
    state: QuizUiState,
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomClearance: Dp = ScreenLayout.BottomClearanceFloating
) {
    val fontScalePreference = state.fontScalePreference

    RichTextScaleProvider(proseScale = fontScalePreference ?: 1f) {
        QuestionContent(
            state = state,
            viewModel = viewModel,
            mediaHandler = mediaHandler,
            contentPadding = contentPadding,
            bottomClearance = bottomClearance,
        )
    }
}

@Composable
private fun QuestionContent(
    state: QuizUiState,
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    contentPadding: PaddingValues,
    bottomClearance: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = ScreenLayout.WideWidthBreakpoint)
                ) {
                    QuizQuestionCard(
                        state = state,
                        viewModel = viewModel,
                        mediaHandler = mediaHandler,
                        bottomClearance = bottomClearance
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
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    bottomClearance: Dp
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = state.isLoading,
                enter = fadeIn(animationSpec = defaultEffectsSpec),
                exit = fadeOut(animationSpec = defaultEffectsSpec)
            ) {
                LoadingIndicator()
            }
            AnimatedVisibility(
                visible = !state.isLoading,
                enter = fadeIn(animationSpec = defaultEffectsSpec),
                exit = fadeOut(animationSpec = defaultEffectsSpec)
            ) {
                Text(
                    text = "Select a question to begin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    val correctAnswerId = remember(question.id, answers) {
        answers.getOrNull(question.corrAns - 1)?.answerId?.toInt()
    }
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
    var hintExpanded by rememberSaveable(question.id) { mutableStateOf(false) }
    val showHint = hintHtml != null && (state.answerSubmitted || hintExpanded)

    val scrollState = key(state.currentQuestionIndex) { rememberScrollState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.Medium)
            .padding(top = Spacing.MediumSmall, bottom = bottomClearance + Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        key(state.currentQuestionIndex) {
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
                    highlightsRepository = viewModel.highlightsRepository,
                    showSelectedHighlight = state.answerSubmitted,
                    onLinkClick = linkHandler,
                    onMediaClick = mediaClick,
                    onShowSnackbar = viewModel::emitSnackbar,
                    modifier = Modifier.padding(Spacing.Medium)
                )
            }

            if (hintHtml != null) {
                HintSection(
                    isVisible = showHint,
                    canToggle = !state.answerSubmitted,
                    onToggle = { hintExpanded = !hintExpanded },
                    hintHtml = hintHtml,
                    linkHandler = linkHandler,
                    mediaClick = mediaClick,
                    showSelectedHighlight = state.answerSubmitted
                )
            }

            AnswerOptions(
                answers = answers,
                sanitizedAnswers = sanitizedAnswers,
                selectedAnswerId = state.selectedAnswerId,
                correctAnswerId = correctAnswerId,
                answerSubmitted = state.answerSubmitted,
                answerPercentages = answerPercentages,
                onAnswerSelected = { answerId ->
                    if (!state.answerSubmitted) {
                        viewModel.onAnswerSelected(answerId)
                        if (state.submissionMode == SubmissionMode.INSTANT) {
                            viewModel.submitAnswer(timeTaken = 0L)
                        }
                    }
                },
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
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 1.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
                    ) {
                        Text(
                            text = "Explanation",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HighlightableRichText(
                            html = explanationHtml,
                            section = HighlightSection.EXPLANATION,
                            highlightsRepository = viewModel.highlightsRepository,
                            showSelectedHighlight = state.answerSubmitted,
                            onLinkClick = linkHandler,
                            onMediaClick = mediaClick,
                            onShowSnackbar = viewModel::emitSnackbar
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
            ) {
                Column {
                    Spacer(modifier = Modifier.height(Spacing.MediumSmall))
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
            ) {
                Column {
                    Spacer(modifier = Modifier.height(Spacing.MediumSmall))
                    PerformanceCard(performance = state.currentPerformance)
                }
            }
        }
    }
}
