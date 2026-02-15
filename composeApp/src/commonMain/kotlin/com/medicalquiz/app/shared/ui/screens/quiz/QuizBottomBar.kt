package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class QuizBottomToolbarUiState(
    val currentQuestionIndex: Int,
    val totalQuestions: Int,
    val hasPreviousQuestion: Boolean,
    val hasNextQuestion: Boolean,
)

/**
 * Quiz navigation toolbar using IconButtons for compact navigation.
 *
 * Per M3 guidelines:
 * - IconButtons are used for supplementary actions in toolbars
 * - Previous/Next are navigation actions (not primary content actions)
 * - Question counter is the primary focus, navigation is supplementary
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
                // Previous - standard IconButton (supplementary navigation)
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
            },
            trailingContent = {
                // Next - FilledTonalIconButton (slightly more emphasis as primary navigation)
                FilledTonalIconButton(
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
            },
            content = {
                // Spacer to add spacing between leadingContent and content
                Spacer(modifier = Modifier.width(8.dp))
                
                // Question counter - centered with pill container
                Surface(
                    onClick = onJumpTo,
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
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
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
