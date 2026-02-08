package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.shared.data.database.QuestionPerformance
import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.data.models.HighlightSection
import com.medicalquiz.app.shared.data.models.Question
import com.medicalquiz.app.shared.ui.media.MediaHandler
import com.medicalquiz.app.shared.ui.richtext.HighlightableRichText
import com.medicalquiz.app.shared.ui.richtext.RichText
import com.medicalquiz.app.shared.ui.screens.quiz.AnswerOptions
import com.medicalquiz.app.shared.ui.state.QuizUiState
import com.medicalquiz.app.shared.utils.HtmlUtils
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import kotlin.math.roundToInt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuizScreen(
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: () -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    QuestionContent(
        state = state,
        viewModel = viewModel,
        mediaHandler = mediaHandler,
        contentPadding = contentPadding
    )
}

@Composable
private fun QuestionContent(
    state: QuizUiState,
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler,
    contentPadding: PaddingValues
) {
    val metadataSections = buildMetadataSections(state.currentQuestion)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 80.dp)
    ) {
        // Question content stays primary, metadata/logs follow below
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.large
        ) {
            QuizQuestionCard(
                state = state,
                viewModel = viewModel,
                mediaHandler = mediaHandler
            )
        }

        // Question metadata - shown after answering
        AnimatedVisibility(
            visible = state.showMetadata && state.answerSubmitted && metadataSections.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                QuestionMetadataCard(sections = metadataSections)
            }
        }

        // Performance logs - shown after answering if logs enabled
        AnimatedVisibility(
            visible = state.answerSubmitted && state.currentPerformance != null && state.isLoggingEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                PerformanceCard(performance = state.currentPerformance)
            }
        }
    }
}

@Composable
private fun HintSection(
    isVisible: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit,
    hintHtml: String,
    linkHandler: (String) -> Unit,
    mediaClick: (String) -> Unit,
    showSelectedHighlight: Boolean
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(
            alpha = if (isVisible) 1f else 0.6f
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = if (canToggle) onToggle else ({})
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💡 Hint",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (canToggle) {
                    Text(
                        text = if (isVisible) "▲" else "▼",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                RichText(
                    html = hintHtml,
                    modifier = Modifier.padding(top = 8.dp),
                    onLinkClick = linkHandler,
                    onMediaClick = mediaClick,
                    showSelectedHighlight = showSelectedHighlight
                )
            }
        }
    }
}

@Composable
private fun QuizQuestionCard(
    state: QuizUiState,
    viewModel: QuizViewModel,
    mediaHandler: MediaHandler
) {
    val question = state.currentQuestion
    val answers = state.currentAnswers
    val uriHandler = LocalUriHandler.current
    val linkHandler: (String) -> Unit = remember(question?.id, mediaHandler) {
        { url ->
            val normalizedUrl = url.trim()
            if (normalizedUrl.isEmpty()) return@remember
            if (!mediaHandler.handleMediaLink(normalizedUrl)) {
                try {
                    uriHandler.openUri(normalizedUrl)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
    val mediaClick: (String) -> Unit = remember(mediaHandler) { { ref -> mediaHandler.handleMediaLink(ref) } }

    LaunchedEffect(question?.id) {
        if (question != null) {
            // Run regex-heavy media collection off the main thread
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
            Text(text = "Select a question to begin", style = MaterialTheme.typography.bodyMedium)
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
            // Use largest remainder method to ensure percentages sum to 100
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

    // Create a scroll state that persists across recompositions
    val scrollState = remember(question.id) {
        val initialPosition = viewModel.getScrollPosition(question.id)
        androidx.compose.foundation.ScrollState(initialPosition)
    }

    // Save scroll position when it changes
    LaunchedEffect(scrollState.value) {
        viewModel.saveScrollPosition(question.id, scrollState.value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Use HighlightableRichText for question content
        HighlightableRichText(
            html = questionHtml,
            section = HighlightSection.QUESTION,
            highlightsRepository = viewModel.getTextHighlightsRepository(),
            showSelectedHighlight = state.answerSubmitted,
            onLinkClick = linkHandler,
            onMediaClick = mediaClick
        )

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
                    viewModel.submitAnswer(timeTaken = 0L)
                }
            },
            onLinkClick = linkHandler,
            onMediaClick = mediaClick
        )

        AnimatedVisibility(
            visible = state.answerSubmitted && explanationHtml.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Explanation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    // Use HighlightableRichText for explanation
                    HighlightableRichText(
                        html = explanationHtml,
                        section = HighlightSection.EXPLANATION,
                        highlightsRepository = viewModel.getTextHighlightsRepository(),
                        showSelectedHighlight = state.answerSubmitted,
                        onLinkClick = linkHandler,
                        onMediaClick = mediaClick
                    )
                }
            }
        }

    }
}

@Composable
private fun QuestionMetadataCard(sections: List<MetadataSection>) {
    if (sections.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sections.forEach { section ->
                when (section) {
                    is MetadataSection.Chips -> MetadataChipGroupRow(section.label, section.values)
                }
            }
        }
    }
}

private sealed interface MetadataSection {
    data class Chips(val label: String, val values: List<String>) : MetadataSection
}

@Composable
private fun buildMetadataSections(question: Question?): List<MetadataSection> {
    val currentQuestion = question ?: return emptyList()

    return remember(currentQuestion.id, currentQuestion.subName, currentQuestion.sysName) {
        val sections = mutableListOf<MetadataSection>()
        sections += MetadataSection.Chips(label = "ID", values = listOf("#${currentQuestion.id}"))

        extractMetadataList(currentQuestion.subName)
            .takeIf { it.isNotEmpty() }
            ?.let { values ->
                val label = if (values.size == 1) "Subject" else "Subjects"
                sections += MetadataSection.Chips(label, values)
            }

        extractMetadataList(currentQuestion.sysName)
            .takeIf { it.isNotEmpty() }
            ?.let { values ->
                val label = if (values.size == 1) "System" else "Systems"
                sections += MetadataSection.Chips(label, values)
            }

        sections
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataChipGroupRow(label: String, values: List<String>) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor.copy(alpha = 0.75f)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            values.forEach { value ->
                MetadataTag(text = value)
            }
        }
    }
}

@Composable
private fun MetadataTag(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val metadataDelimiters = Regex("[,;\\n•]+")

private fun extractMetadataList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(metadataDelimiters)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

@Composable
private fun PerformanceCard(performance: QuestionPerformance?) {
    performance ?: return
    
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val lastResultColor = if (performance.lastCorrect) 
        MaterialTheme.colorScheme.tertiary 
    else 
        MaterialTheme.colorScheme.error
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerformanceStat(
                label = "Attempts",
                value = performance.attempts.toString(),
                color = contentColor
            )
            
            PerformanceStat(
                label = "Last",
                value = if (performance.lastCorrect) "✓" else "✗",
                color = lastResultColor
            )
            
            PerformanceStat(
                label = "Score",
                value = "${performance.correctCount}/${performance.correctCount + performance.incorrectCount}",
                color = contentColor
            )
        }
    }
}

@Composable
private fun PerformanceStat(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}
