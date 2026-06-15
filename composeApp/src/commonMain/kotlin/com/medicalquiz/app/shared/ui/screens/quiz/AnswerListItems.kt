package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.medicalquiz.app.shared.data.models.Answer
import kotlin.math.min
import com.medicalquiz.app.shared.ui.richtext.RichText

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
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Note: in "Instant Feedback" mode, the isSelected (primaryContainer) color is 
    // effectively skipped as showResult triggers simultaneously. It is preserved 
    // for potential "Select then Submit" modes and to drive other UI traits 
    // like elevation and badge shape morphing.
    val containerColor by animateColorAsState(
        targetValue = when {
            showResult && isCorrect -> MaterialTheme.colorScheme.secondaryContainer
            showResult && isSelected && !isCorrect -> MaterialTheme.colorScheme.errorContainer
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            showResult && isCorrect -> MaterialTheme.colorScheme.onSecondaryContainer
            showResult && isSelected && !isCorrect -> MaterialTheme.colorScheme.onErrorContainer
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "contentColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "scale"
    )

    val elevation by animateDpAsState(
        targetValue = when {
            isPressed -> 4.dp
            isSelected -> 2.dp
            else -> 0.dp
        },
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "elevation"
    )

    val leadingContent: @Composable () -> Unit = {
        val labelContainerColor by animateColorAsState(
            targetValue = when {
                showResult && isCorrect -> MaterialTheme.colorScheme.secondary
                showResult && isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            animationSpec = motionScheme.defaultEffectsSpec()
        )
        val labelContentColor by animateColorAsState(
            targetValue = when {
                showResult && isCorrect -> MaterialTheme.colorScheme.onSecondary
                showResult && isSelected && !isCorrect -> MaterialTheme.colorScheme.onError
                isSelected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = motionScheme.defaultEffectsSpec()
        )

        val targetShape = when {
            showResult && isCorrect -> MaterialShapes.Gem
            isSelected && !showResult -> MaterialShapes.Sunny
            else -> MaterialShapes.Pill
        }

        MorphingMaterialShapeBadge(
            targetShape = targetShape,
            backgroundColor = labelContainerColor,
            size = 40.dp,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                        fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                },
                label = "label"
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

    val trailingTextColor = when {
        showResult && isCorrect -> MaterialTheme.colorScheme.onSecondaryContainer
        showResult -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val trailingContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = showResult to percentage,
            transitionSpec = {
                fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec())
            },
            label = "trailingContent"
        ) { (show, pct) ->
            when {
                show && pct != null -> {
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = trailingTextColor,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                else -> Spacer(modifier = Modifier.size(0.dp))
            }
        }
    }

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
                containerColor = containerColor,
                headlineColor = contentColor,
                leadingIconColor = contentColor,
                trailingIconColor = contentColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingMaterialShapeBadge(
    targetShape: RoundedPolygon,
    backgroundColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val morphSpatialSpec = motionScheme.defaultSpatialSpec<Float>()

    var previousShape by remember { mutableStateOf(targetShape) }
    var currentShape by remember { mutableStateOf(targetShape) }
    val progress = remember { Animatable(1f) }
    val cachedPath = remember { Path() }
    val matrix = remember { Matrix() }

    LaunchedEffect(targetShape) {
        if (targetShape != currentShape) {
            previousShape = currentShape
            currentShape = targetShape
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = morphSpatialSpec)
        }
    }

    val morph = remember(previousShape, currentShape) { Morph(previousShape, currentShape) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .drawWithCache {
                val minDimension = min(this@drawWithCache.size.width, this@drawWithCache.size.height)
                cachedPath.rewind()
                val shapePath = morph.toPath(
                    progress = progress.value,
                    path = cachedPath,
                    startAngle = 0
                )
                shapePath.transform(
                    matrix.apply {
                        resetToPivotedTransform(
                            pivotX = 0.5f,
                            pivotY = 0.5f,
                            translationX = this@drawWithCache.size.width / 2f,
                            translationY = this@drawWithCache.size.height / 2f,
                            scaleX = minDimension,
                            scaleY = minDimension
                        )
                    }
                )
                onDrawBehind {
                    drawPath(path = shapePath, color = backgroundColor, style = Fill)
                }
            }
    ) {
        content()
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
