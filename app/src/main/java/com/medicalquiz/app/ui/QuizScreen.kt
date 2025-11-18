package com.medicalquiz.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.data.models.Question
import com.medicalquiz.app.data.database.QuestionPerformance
import com.medicalquiz.app.utils.WebViewComposable
import com.medicalquiz.app.utils.WebViewState
import com.medicalquiz.app.viewmodel.UiEvent
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun QuizScreen(
    viewModel: com.medicalquiz.app.viewmodel.QuizViewModel,
    webViewStateFlow: MutableStateFlow<WebViewState>,
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
        webViewStateFlow = webViewStateFlow,
        mediaHandler = mediaHandler,
        contentPadding = contentPadding
    )

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

