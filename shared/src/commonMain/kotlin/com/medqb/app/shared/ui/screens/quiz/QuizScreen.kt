package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.adaptive.LocalAdaptiveLayoutInfo
import com.medqb.app.shared.ui.components.ContentMaxWidth
import com.medqb.app.shared.ui.components.ResponsiveContentContainer
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.richtext.RichText
import com.medqb.app.shared.ui.richtext.RichTextHighlightConfig
import com.medqb.app.shared.ui.richtext.RichTextHighlightHost
import com.medqb.app.shared.ui.richtext.RichTextScaleProvider
import com.medqb.app.shared.ui.richtext.rememberRichTextHighlightHostState
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
    onHighlightAdd: (HighlightSection, String, Int, Int, String, HighlightColor) -> Unit,
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
    onHighlightAdd: (HighlightSection, String, Int, Int, String, HighlightColor) -> Unit,
    onHighlightRemove: (Long) -> Unit,
    onHighlightColorChange: (Long, HighlightColor) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit,
    onRetry: () -> Unit,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomClearance: Dp = 80.dp,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("quiz_surface"),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
        ) {
            val adaptive = LocalAdaptiveLayoutInfo.current
            ResponsiveContentContainer(
                maxWidth = if (adaptive.useTwoPaneLayout) ContentMaxWidth.TwoPane else ContentMaxWidth.Standard,
                modifier = Modifier.weight(1f).fillMaxWidth(),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuizQuestionCard(
    state: QuizUiState,
    mediaHandler: MediaHandler,
    onAnswerSelected: (Long) -> Unit,
    onHighlightAdd: (HighlightSection, String, Int, Int, String, HighlightColor) -> Unit,
    onHighlightRemove: (Long) -> Unit,
    onHighlightColorChange: (Long, HighlightColor) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit,
    onRetry: () -> Unit,
    onNavigateHome: () -> Unit,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
) {
    val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

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
    val mediaClick: (String, String) -> Unit = remember(mediaHandler) {
        { ref, ownerKey -> mediaHandler.handleMediaLink(ref, ownerKey) }
    }

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

    val adaptive = LocalAdaptiveLayoutInfo.current
    val highlightHostState = rememberRichTextHighlightHostState(question.id)

    RichTextHighlightHost(
        state = highlightHostState,
        modifier = modifier.fillMaxSize(),
    ) {
        if (adaptive.useTwoPaneLayout) {
            val leftScrollState = key(state.currentQuestionIndex) { rememberScrollState() }
            val rightScrollState = key(state.currentQuestionIndex) { rememberScrollState() }

            LaunchedEffect(leftScrollState.isScrollInProgress, rightScrollState.isScrollInProgress) {
                if (leftScrollState.isScrollInProgress || rightScrollState.isScrollInProgress) {
                    highlightHostState.dismiss()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = adaptive.horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(leftScrollState)
                        .padding(top = 12.dp, bottom = bottomClearance + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    key(question.id) {
                        QuestionStemCard(
                            questionId = question.id,
                            questionHtml = questionHtml,
                            highlights = state.questionHighlights,
                            isKeyInfoActive = isKeyInfoActive,
                            onHighlightAdd = onHighlightAdd,
                            onHighlightRemove = onHighlightRemove,
                            onHighlightColorChange = onHighlightColorChange,
                            linkHandler = linkHandler,
                            mediaClick = mediaClick,
                            onShowSnackbar = onShowSnackbar,
                        )
                    }

                    QuizStudySection(
                        questionId = question.id,
                        hasKeyInfo = hasKeyInfo,
                        isKeyInfoActive = isKeyInfoActive,
                        onToggleKeyInfo = {
                            highlightHostState.dismiss()
                            keyInfoToggled = !isKeyInfoActive
                        },
                        hasAttendingTip = hintHtml != null,
                        isAttendingTipActive = attendingTipVisible,
                        onToggleAttendingTip = {
                            highlightHostState.dismiss()
                            attendingTipVisible = !attendingTipVisible
                        },
                        isLabsActive = showLabsSheet,
                        onOpenLabs = {
                            highlightHostState.dismiss()
                            showLabsSheet = true
                        },
                        hintHtml = hintHtml,
                        linkHandler = linkHandler,
                        mediaClick = mediaClick,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rightScrollState)
                        .padding(top = 12.dp, bottom = bottomClearance + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AnswerOptions(
                        questionId = question.id,
                        answers = answers,
                        sanitizedAnswers = sanitizedAnswers,
                        selectedAnswerId = state.selectedAnswerId,
                        correctAnswerId = correctAnswerId,
                        answerSubmitted = state.answerSubmitted,
                        answerPercentages = answerPercentages,
                        onAnswerSelected = { answerId ->
                            highlightHostState.dismiss()
                            onAnswerSelected(answerId)
                        },
                        onLinkClick = linkHandler,
                        onMediaClick = mediaClick,
                    )

                    key(question.id) {
                        QuizExplanationAndMetadata(
                            state = state,
                            explanationHtml = explanationHtml,
                            metadataSections = metadataSections,
                            onHighlightAdd = onHighlightAdd,
                            onHighlightRemove = onHighlightRemove,
                            onHighlightColorChange = onHighlightColorChange,
                            linkHandler = linkHandler,
                            mediaClick = mediaClick,
                            onShowSnackbar = onShowSnackbar,
                        )
                    }
                }
            }
        } else {
            val scrollState = key(state.currentQuestionIndex) { rememberScrollState() }

            LaunchedEffect(scrollState.isScrollInProgress) {
                if (scrollState.isScrollInProgress) {
                    highlightHostState.dismiss()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = adaptive.horizontalPadding)
                    .padding(top = 12.dp, bottom = bottomClearance + 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                key(question.id) {
                    QuestionStemCard(
                        questionId = question.id,
                        questionHtml = questionHtml,
                        highlights = state.questionHighlights,
                        isKeyInfoActive = isKeyInfoActive,
                        onHighlightAdd = onHighlightAdd,
                        onHighlightRemove = onHighlightRemove,
                        onHighlightColorChange = onHighlightColorChange,
                        linkHandler = linkHandler,
                        mediaClick = mediaClick,
                        onShowSnackbar = onShowSnackbar,
                    )
                }

                QuizStudySection(
                    questionId = question.id,
                    hasKeyInfo = hasKeyInfo,
                    isKeyInfoActive = isKeyInfoActive,
                    onToggleKeyInfo = {
                        highlightHostState.dismiss()
                        keyInfoToggled = !isKeyInfoActive
                    },
                    hasAttendingTip = hintHtml != null,
                    isAttendingTipActive = attendingTipVisible,
                    onToggleAttendingTip = {
                        highlightHostState.dismiss()
                        attendingTipVisible = !attendingTipVisible
                    },
                    isLabsActive = showLabsSheet,
                    onOpenLabs = {
                        highlightHostState.dismiss()
                        showLabsSheet = true
                    },
                    hintHtml = hintHtml,
                    linkHandler = linkHandler,
                    mediaClick = mediaClick,
                )

                AnswerOptions(
                    questionId = question.id,
                    answers = answers,
                    sanitizedAnswers = sanitizedAnswers,
                    selectedAnswerId = state.selectedAnswerId,
                    correctAnswerId = correctAnswerId,
                    answerSubmitted = state.answerSubmitted,
                    answerPercentages = answerPercentages,
                    onAnswerSelected = { answerId ->
                        highlightHostState.dismiss()
                        onAnswerSelected(answerId)
                    },
                    onLinkClick = linkHandler,
                    onMediaClick = mediaClick
                )

                key(question.id) {
                    QuizExplanationAndMetadata(
                        state = state,
                        explanationHtml = explanationHtml,
                        metadataSections = metadataSections,
                        onHighlightAdd = onHighlightAdd,
                        onHighlightRemove = onHighlightRemove,
                        onHighlightColorChange = onHighlightColorChange,
                        linkHandler = linkHandler,
                        mediaClick = mediaClick,
                        onShowSnackbar = onShowSnackbar,
                    )
                }
            }
        }
    }

    if (showLabsSheet) {
        LabsBottomSheet(onDismiss = { showLabsSheet = false })
    }
}

@Composable
private fun QuestionStemCard(
    questionId: Long,
    questionHtml: String,
    highlights: List<TextHighlight>,
    isKeyInfoActive: Boolean,
    onHighlightAdd: (HighlightSection, String, Int, Int, String, HighlightColor) -> Unit,
    onHighlightRemove: (Long) -> Unit,
    onHighlightColorChange: (Long, HighlightColor) -> Unit,
    linkHandler: (String) -> Unit,
    mediaClick: (String, String) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit,
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
        RichText(
            html = questionHtml,
            showSelectedHighlight = isKeyInfoActive,
            onLinkClick = linkHandler,
            onMediaWithOwnerClick = mediaClick,
            mediaOwnerPrefix = "q$questionId:stem",
            highlightConfig = RichTextHighlightConfig(
                section = HighlightSection.QUESTION,
                highlights = highlights,
                onHighlightAdd = onHighlightAdd,
                onHighlightRemove = onHighlightRemove,
                onHighlightColorChange = onHighlightColorChange,
                onShowSnackbar = onShowSnackbar,
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuizExplanationAndMetadata(
    state: QuizUiState,
    explanationHtml: String,
    metadataSections: List<MetadataSection>,
    onHighlightAdd: (HighlightSection, String, Int, Int, String, HighlightColor) -> Unit,
    onHighlightRemove: (Long) -> Unit,
    onHighlightColorChange: (Long, HighlightColor) -> Unit,
    linkHandler: (String) -> Unit,
    mediaClick: (String, String) -> Unit,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit,
) {
    val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val defaultSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

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
                RichText(
                    html = explanationHtml,
                    showSelectedHighlight = state.answerSubmitted,
                    onLinkClick = linkHandler,
                    onMediaWithOwnerClick = mediaClick,
                    mediaOwnerPrefix = "q${state.currentQuestion?.id ?: 0L}:explanation",
                    highlightConfig = RichTextHighlightConfig(
                        section = HighlightSection.EXPLANATION,
                        highlights = state.explanationHighlights,
                        onHighlightAdd = onHighlightAdd,
                        onHighlightRemove = onHighlightRemove,
                        onHighlightColorChange = onHighlightColorChange,
                        onShowSnackbar = onShowSnackbar,
                    ),
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
