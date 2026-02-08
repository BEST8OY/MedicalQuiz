package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
 * Answer list item following Material 3 Expressive guidelines for lists.
 *
 * Uses ListItemDefaults.shapes() for proper rounded corners in all states.
 * Per M3 Expressive:
 * - shape: rounded corners for default state
 * - selectedShape: rounded corners when selected
 * - Use selectedContainerColor/selectedContentColor for selection states
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    // Determine result state colors (post-answer)
    val resultContainerColor = when {
        showResult && isCorrect -> colors.tertiaryContainer
        showResult && isSelected && !isCorrect -> colors.errorContainer
        else -> null
    }

    val resultContentColor = when {
        showResult && isCorrect -> colors.onTertiaryContainer
        showResult && isSelected && !isCorrect -> colors.onErrorContainer
        else -> null
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

    if (showResult && resultContainerColor != null) {
        // Post-answer: Use Surface wrapper for result colors (tertiary/error containers)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = resultContainerColor,
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
                    containerColor = resultContainerColor,
                    headlineColor = resultContentColor ?: colors.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        // Pre-answer or neutral: Use ListItem with proper shapes and selection colors
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
                containerColor = colors.surfaceContainerLowest,
                headlineColor = colors.onSurface,
                selectedContainerColor = colors.primaryContainer,
                selectedContentColor = colors.onPrimaryContainer
            ),
            shapes = ListItemDefaults.shapes(
                shape = MaterialTheme.shapes.medium,
                selectedShape = MaterialTheme.shapes.medium
            ),
            selected = isSelected,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
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
