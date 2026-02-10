package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class QuizBottomToolbarUiState(
    val currentQuestionIndex: Int,
    val totalQuestions: Int,
    val hasPreviousQuestion: Boolean,
    val hasNextQuestion: Boolean,
)

/**
 * Quiz navigation toolbar following Material 3 Expressive guidelines.
 *
 * Key design decisions:
 * - Uses standard icon buttons (not filled/square) per M3 guidelines
 * - Next is the primary action (FilledTonalButton in expanded mode)
 * - Previous is secondary (OutlinedButton in expanded, IconButton in compact)
 * - Question counter shown as simple text in content area
 * - Always expanded since navigation should always be visible
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizFloatingToolbar(
    uiState: QuizBottomToolbarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Always expanded - navigation controls should always be visible
    BoxWithConstraints(modifier = modifier) {
        val isExpanded = maxWidth >= 600.dp
        val currentQuestionNumber = uiState.currentQuestionIndex + 1
        val questionLabel = "$currentQuestionNumber / ${uiState.totalQuestions}"

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.padding(horizontal = if (isExpanded) 24.dp else 16.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            leadingContent = {
                // Previous button - secondary action
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
                    // Use standard IconButton (not filled/square) per M3 guidelines
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
                // Next button - primary action (highest emphasis)
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
                    // Use standard IconButton (not filled/square) per M3 guidelines
                    IconButton(
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
                // Spacer to add spacing between leadingContent and content
                Spacer(modifier = Modifier.width(8.dp))
                
                // Question counter - centered with pill container
                Surface(
                    onClick = onJumpTo,
                    shape = MaterialShapes.Pill,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .sizeIn(minHeight = 40.dp)
                        .semantics {
                            contentDescription = "Question $currentQuestionNumber of ${uiState.totalQuestions}. Tap to jump."
                        },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = questionLabel,
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                
                // Spacer to add spacing between content and trailingContent
                Spacer(modifier = Modifier.width(8.dp))
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
