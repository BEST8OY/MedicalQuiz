package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
 * - Use lists for selecting discrete items
 * - Leading element: A, B, C, D label
 * - Headline: Answer text (HTML content)
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
    onMediaClick: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // Determine container color based on state
    val containerColor = when {
        showResult && isCorrect -> colors.tertiaryContainer
        showResult && isSelected && !isCorrect -> colors.errorContainer
        isSelected -> colors.primaryContainer
        else -> colors.surfaceContainerLowest
    }

    // Leading element: Label (A, B, C, D) with radio button style selection
    val leadingContent: @Composable () -> Unit = {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (isSelected) colors.primaryContainer else colors.surfaceContainerHigh,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
                )
            }
        }
    }

    // Trailing element: Result indicator
    val trailingContent: @Composable () -> Unit = {
        when {
            showResult && percentage != null -> {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colors.secondaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        color = colors.onSecondaryContainer
                    )
                }
            }
            showResult && isCorrect -> {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.tertiary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            showResult && isSelected && !isCorrect -> {
                Text(
                    text = "✗",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            else -> {
                // No trailing element needed - selection shown via background color
            }
        }
    }

    // Use Surface wrapper for rounded corners and background color
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        ListItem(
            headlineContent = {
                RichText(
                    html = html,
                    onLinkClick = onLinkClick,
                    onMediaClick = onMediaClick,
                    showSelectedHighlight = showResult
                )
            },
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(
                containerColor = containerColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Answer options section using List-based layout.
 *
 * Per M3 Guidelines:
 * - Use spacing between list items for cleaner appearance
 * - Lists are for discrete, selectable items
 * - Each item should have consistent layout
 */
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
    onMediaClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                onMediaClick = onMediaClick
            )
        }
    }
}
