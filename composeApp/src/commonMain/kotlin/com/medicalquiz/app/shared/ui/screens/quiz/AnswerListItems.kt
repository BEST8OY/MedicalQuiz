package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.ui.richtext.RichText

/**
 * Answer list item following Material 3 guidelines for lists with MotionScheme animations.
 *
 * Per M3 guidelines:
 * - Use lists for selecting discrete items
 * - Leading element: A, B, C, D label
 * - Headline: Answer text (HTML content)
 * - Trailing: Result indicator (percentage or checkmark/x)
 * - Selection state applies to entire list item
 * - Press animations using MotionScheme for tactile feedback
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
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate container color using MotionScheme
    val containerColor by animateColorAsState(
        targetValue = when {
            showResult && isCorrect -> colors.tertiaryContainer
            showResult && isSelected && !isCorrect -> colors.errorContainer
            isSelected -> colors.primaryContainer
            else -> colors.surfaceContainerLowest
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "containerColor"
    )

    // Animate content color using MotionScheme
    val contentColor by animateColorAsState(
        targetValue = when {
            showResult && isCorrect -> colors.onTertiaryContainer
            showResult && isSelected && !isCorrect -> colors.onErrorContainer
            isSelected -> colors.onPrimaryContainer
            else -> colors.onSurface
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "contentColor"
    )

    // Animate scale for press feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Animate elevation for tactile feedback
    val elevation by animateDpAsState(
        targetValue = when {
            isPressed -> 4.dp
            isSelected -> 2.dp
            else -> 0.dp
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "elevation"
    )

    // Leading element: Label (A, B, C, D) with animated selection state
    val leadingContent: @Composable () -> Unit = {
        val labelContainerColor by animateColorAsState(
            targetValue = if (isSelected) colors.primary else colors.surfaceContainerHigh,
            animationSpec = motionScheme.defaultEffectsSpec()
        )
        val labelContentColor by animateColorAsState(
            targetValue = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
            animationSpec = motionScheme.defaultEffectsSpec()
        )

        Surface(
            shape = MaterialTheme.shapes.small,
            color = labelContainerColor,
            shadowElevation = if (isSelected) 2.dp else 0.dp,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                        fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                    }
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = labelContentColor
                    )
                }
            }
        }
    }

    // Trailing element: Result indicator with MotionScheme animations
    val trailingContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = Triple(showResult, isCorrect, percentage),
            transitionSpec = {
                scaleIn(
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    initialScale = 0.7f
                ) togetherWith scaleOut(
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    targetScale = 0.7f
                )
            }
        ) { (show, correct, pct) ->
            when {
                show && pct != null -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = colors.secondaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            color = colors.onSecondaryContainer
                        )
                    }
                }
                show && correct -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = colors.tertiaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Correct",
                            tint = colors.onTertiaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                show && !correct -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = colors.errorContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Incorrect",
                            tint = colors.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                else -> {
                    // No trailing element - selection shown via background color
                    Box(modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    // Use Surface wrapper with morphing shapes based on answer state
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        shadowElevation = elevation,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
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
 * Answer options section using List-based layout with MotionScheme animations.
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
