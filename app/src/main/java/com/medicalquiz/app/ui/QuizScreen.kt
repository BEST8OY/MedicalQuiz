package com.medicalquiz.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import com.medicalquiz.app.data.database.QuestionPerformance
import com.medicalquiz.app.ui.richtext.RichText
import com.medicalquiz.app.utils.HtmlUtils

@Composable
fun QuizScreen(
    viewModel: com.medicalquiz.app.viewmodel.QuizViewModel,
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
    state: QuizState,
    viewModel: com.medicalquiz.app.viewmodel.QuizViewModel,
    mediaHandler: MediaHandler,
    contentPadding: PaddingValues
) {
    val metadataSections = remember(state.currentQuestion) {
        buildMetadataSections(state.currentQuestion)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Question content stays primary, metadata/logs follow below
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(20.dp)
        ) {
            QuizQuestionCard(
                state = state,
                viewModel = viewModel,
                mediaHandler = mediaHandler
            )
        }

        // Question metadata - shown after answering
        AnimatedVisibility(
            visible = state.answerSubmitted && metadataSections.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            QuestionMetadataCard(sections = metadataSections)
        }

        // Performance logs - shown after answering if logs enabled
        AnimatedVisibility(
            visible = state.answerSubmitted && state.currentPerformance != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            PerformanceCard(performance = state.currentPerformance)
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
    mediaClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hint",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (canToggle) {
                TextButton(onClick = onToggle) {
                    Text(text = if (isVisible) "Hide" else "Show")
                }
            }
        }
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                RichText(
                    html = hintHtml,
                    modifier = Modifier.padding(12.dp),
                    onLinkClick = linkHandler,
                    onMediaClick = mediaClick
                )
            }
        }
    }
}

@Composable
private fun AnswerOptions(
    answers: List<Answer>,
    sanitizedAnswers: Map<Long, String>,
    selectedAnswerId: Int?,
    correctAnswerId: Int?,
    answerSubmitted: Boolean,
    answerPercentages: Map<Long, Int?>,
    onAnswerSelected: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        answers.forEachIndexed { index, answer ->
            val label = ('A'.code + index).toChar().toString()
            val html = sanitizedAnswers[answer.answerId].orEmpty()
            val isSelected = answer.answerId.toInt() == selectedAnswerId
            val isCorrect = answer.answerId.toInt() == correctAnswerId
            val percentage = answerPercentages[answer.answerId]
            AnswerCard(
                label = label,
                html = html,
                isSelected = isSelected,
                isCorrect = isCorrect,
                showResult = answerSubmitted,
                percentage = percentage,
                enabled = !answerSubmitted,
                onClick = { onAnswerSelected(answer.answerId) },
                onLinkClick = onLinkClick,
                onMediaClick = onMediaClick
            )
        }
    }
}

@Composable
private fun AnswerCard(
    label: String,
    html: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    showResult: Boolean,
    percentage: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        showResult && isCorrect -> colors.tertiaryContainer
        showResult && isSelected && !isCorrect -> colors.errorContainer
        isSelected -> colors.primaryContainer
        else -> colors.surfaceVariant
    }
    val borderColor = when {
        showResult && isCorrect -> colors.tertiary
        showResult && isSelected && !isCorrect -> colors.error
        isSelected -> colors.primary
        else -> colors.outlineVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = background,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        border = BorderStroke(1.dp, borderColor),
        enabled = enabled,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "$label.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    RichText(
                        html = html,
                        modifier = Modifier.weight(1f),
                        showSelectedHighlight = showResult,
                        onLinkClick = onLinkClick,
                        onMediaClick = onMediaClick
                    )
                }
                if (showResult && percentage != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.secondaryContainer
                    ) {
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizQuestionCard(
    state: QuizState,
    viewModel: com.medicalquiz.app.viewmodel.QuizViewModel,
    mediaHandler: MediaHandler
) {
    val question = state.currentQuestion
    val answers = state.currentAnswers
    val uriHandler = LocalUriHandler.current
    val linkHandler: (String) -> Unit = remember(question?.id, mediaHandler) {
        { url ->
            if (!mediaHandler.handleMediaLink(url)) {
                runCatching { uriHandler.openUri(url) }
            }
        }
    }
    val mediaClick: (String) -> Unit = remember(mediaHandler) { { ref -> mediaHandler.handleMediaLink(ref) } }

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
        HtmlUtils.sanitizeForWebView(question.explanation)
    }
    val sanitizedAnswers = remember(question.id, answers) {
        answers.associate { answer ->
            val normalized = HtmlUtils.normalizeAnswerHtml(answer.answerText)
            val sanitized = HtmlUtils.sanitizeForWebView(normalized)
            answer.answerId to sanitized
        }
    }
    val correctAnswerId = remember(question.id, answers) {
        answers.getOrNull(question.corrAns - 1)?.answerId?.toInt()
    }
    val answerPercentages = remember(answers) {
        val total = answers.sumOf { it.correctPercentage ?: 0 }
        answers.associate { answer ->
            val raw = answer.correctPercentage ?: 0
            val percent = if (total > 0) (raw * 100) / total else null
            answer.answerId to percent
        }
    }
    var hintExpanded by remember(question.id) { mutableStateOf(false) }
    val showHint = hintHtml != null && (state.answerSubmitted || hintExpanded)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        question.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        RichText(
            html = questionHtml,
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
                mediaClick = mediaClick
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Explanation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                RichText(
                    html = explanationHtml,
                    showSelectedHighlight = state.answerSubmitted,
                    onLinkClick = linkHandler,
                    onMediaClick = mediaClick
                )
            }
        }
    }
}

@Composable
private fun QuestionMetadataCard(sections: List<MetadataSection>) {
    if (sections.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
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

private fun buildMetadataSections(question: Question?): List<MetadataSection> {
    val currentQuestion = question ?: return emptyList()

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

    return sections
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
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Attempts: ${performance.attempts}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
            
            Text(
                text = "Last: ${if (performance.lastCorrect) "Correct" else "Incorrect"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
            
            val correct = if (performance.everCorrect) 1 else 0
            val incorrect = if (performance.everIncorrect) 1 else 0
            Text(
                text = "Ratio: $correct/$incorrect",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

