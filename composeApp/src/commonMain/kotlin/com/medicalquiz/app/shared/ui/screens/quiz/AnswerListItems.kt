package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.ui.richtext.RichText

/**
 * Answer list item following Material 3 guidelines for lists.
 *
 * Per M3 guidelines:
 * - Leading element: A, B, C, D label
 * - Main content: Answer text (HTML content)
 * - Trailing: Result indicator (percentage or checkmark/x)
 * - Selection state applies to entire list item
 */
@Composable
private fun AnswerListItem(
    label: String,
    html: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    showResult: Boolean,
    percentage: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    val containerColor = when {
        showResult && isCorrect -> colors.tertiaryContainer
        showResult && isSelected && !isCorrect -> colors.errorContainer
        isSelected -> colors.primaryContainer
        else -> colors.surfaceContainerLowest
    }

    val leadingContent: @Composable () -> Unit = {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (isSelected) colors.primaryContainer else colors.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
        }
    }

    val trailingContent: @Composable () -> Unit = {
        when {
            showResult && percentage != null -> {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colors.secondaryContainer,
                ) {
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        color = colors.onSecondaryContainer,
                    )
                }
            }

            showResult && isCorrect -> {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.tertiary,
                )
            }

            showResult && isSelected && !isCorrect -> {
                Text(
                    text = "✗",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.error,
                )
            }

            else -> Unit
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
                leadingContent()
            }

            Box(modifier = Modifier.weight(1f)) {
                RichText(
                    html = html,
                    onLinkClick = onLinkClick,
                    onMediaClick = onMediaClick,
                    showSelectedHighlight = showResult,
                )
            }

            Box(contentAlignment = Alignment.CenterEnd) {
                trailingContent()
            }
        }
    }
}

@Composable
fun AnswerOptions(
    answers: List<Answer>,
    sanitizedAnswers: Map<Long, String>,
    selectedAnswerId: Int?,
    correctAnswerId: Int?,
    answerSubmitted: Boolean,
    answerPercentages: Map<Long, Int?>,
    onAnswerSelected: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        answers.forEachIndexed { index, answer ->
            val label = ('A'.code + index).toChar().toString()
            val html = sanitizedAnswers[answer.answerId].orEmpty()
            val isSelected = answer.answerId.toInt() == selectedAnswerId
            val isCorrect = answer.answerId.toInt() == correctAnswerId
            val percentage = answerPercentages[answer.answerId]

            AnswerListItem(
                label = label,
                html = html,
                isSelected = isSelected,
                isCorrect = isCorrect,
                showResult = answerSubmitted,
                percentage = percentage,
                enabled = !answerSubmitted,
                onClick = { onAnswerSelected(answer.answerId) },
                onLinkClick = onLinkClick,
                onMediaClick = onMediaClick,
            )
        }
    }
}
