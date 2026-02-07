package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

data class QuizBottomToolbarUiState(
    val currentQuestionIndex: Int,
    val totalQuestions: Int,
    val hasPreviousQuestion: Boolean,
    val hasNextQuestion: Boolean,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizFloatingToolbar(
    uiState: QuizBottomToolbarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isExpanded = maxWidth >= 600.dp
        val currentQuestionNumber = uiState.currentQuestionIndex + 1
        val questionLabel = "$currentQuestionNumber / ${uiState.totalQuestions}"
        val questionDescription = "Question $currentQuestionNumber of ${uiState.totalQuestions}. Double tap to jump."

        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = Modifier.padding(horizontal = if (isExpanded) 24.dp else 16.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            leadingContent = {
                // Previous button
                if (isExpanded) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = uiState.hasPreviousQuestion,
                        modifier = Modifier
                            .sizeIn(minHeight = 48.dp)
                            .semantics { contentDescription = "Previous question" },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                        )
                        Text("Previous")
                    }
                } else {
                    IconButton(
                        onClick = onPrevious,
                        enabled = uiState.hasPreviousQuestion,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = "Previous question" },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                        )
                    }
                }
            },
            trailingContent = {
                // Next button
                if (isExpanded) {
                    FilledTonalButton(
                        onClick = onNext,
                        enabled = uiState.hasNextQuestion,
                        modifier = Modifier
                            .sizeIn(minHeight = 48.dp)
                            .semantics { contentDescription = "Next question" },
                    ) {
                        Text("Next")
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onNext,
                        enabled = uiState.hasNextQuestion,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = "Next question" },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                        )
                    }
                }
            },
            content = {
                // Question counter (jump button) - main content
                AssistChip(
                    onClick = onJumpTo,
                    label = { Text(questionLabel) },
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Jump to question"
                            stateDescription = questionDescription
                        },
                )
            }
        )
    }
}

// Legacy composable for backward compatibility
@Composable
fun QuizBottomBar(
    uiState: QuizBottomToolbarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuizFloatingToolbar(
        uiState = uiState,
        onPrevious = onPrevious,
        onNext = onNext,
        onJumpTo = onJumpTo,
        modifier = modifier
    )
}
