package com.medqb.app.shared.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing

data class QuizBottomToolbarUiState(
    val currentQuestionIndex: Int,
    val totalQuestions: Int,
    val hasPreviousQuestion: Boolean,
    val hasNextQuestion: Boolean,
    val showSubmitButton: Boolean = false,
    val canSubmit: Boolean = false,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizFloatingToolbar(
    uiState: QuizBottomToolbarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: () -> Unit,
    onSubmit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val motionScheme = MaterialTheme.motionScheme
        val isExpanded = maxWidth >= Layout.CompactBreakpoint
        val currentQuestionNumber = uiState.currentQuestionIndex + 1
        val questionLabel = "$currentQuestionNumber / ${uiState.totalQuestions}"

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.padding(horizontal = if (isExpanded) Spacing.Lg else Spacing.Md),
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            contentPadding = FloatingToolbarDefaults.ContentPadding,
            leadingContent = {
                IconButton(
                    onClick = onPrevious,
                    enabled = uiState.hasPreviousQuestion,
                    modifier = Modifier
                        .sizeIn(minWidth = Layout.MinTouchTarget, minHeight = Layout.MinTouchTarget)
                        .semantics { contentDescription = "Previous question" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            trailingContent = {
                IconButton(
                    onClick = onNext,
                    enabled = uiState.hasNextQuestion,
                    modifier = Modifier
                        .sizeIn(minWidth = Layout.MinTouchTarget, minHeight = Layout.MinTouchTarget)
                        .semantics { contentDescription = "Next question" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                    )
                }
            },
            content = {
                Spacer(modifier = Modifier.width(Spacing.Xs))

                AnimatedVisibility(
                    visible = uiState.showSubmitButton,
                    enter = expandHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) +
                            fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                    exit = shrinkHorizontally(animationSpec = motionScheme.fastSpatialSpec()) +
                           fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = onSubmit,
                            enabled = uiState.canSubmit,
                            modifier = Modifier.sizeIn(minHeight = Spacing.MdLg),
                        ) {
                            Text(text = "Submit")
                        }
                        Spacer(modifier = Modifier.width(Spacing.Xs))
                    }
                }

                Surface(
                    onClick = onJumpTo,
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .sizeIn(minHeight = Spacing.MdLg)
                        .semantics {
                            contentDescription = "Question $currentQuestionNumber of ${uiState.totalQuestions}. Tap to jump."
                        },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = questionLabel,
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.Xs))
            }
        )
    }
}
