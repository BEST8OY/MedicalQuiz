package com.medicalquiz.app.shared.ui.screens.quiz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonGroupMenuState

data class QuizBottomToolbarUiState(
    val currentQuestionIndex: Int,
    val totalQuestions: Int,
    val hasPreviousQuestion: Boolean,
    val hasNextQuestion: Boolean,
)

/**
 * Quiz navigation toolbar using Connected Button Group for cohesive navigation.
 *
 * Features:
 * - ConnectedButtonGroup for Previous/Jump/Next navigation
 * - Different shapes for leading/middle/trailing buttons per M3 guidelines
 * - MotionScheme animations for press feedback
 * - Overflow menu for additional actions
 * - Question counter as connected middle button
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
        val menuState = remember { ButtonGroupMenuState() }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.padding(horizontal = if (isExpanded) 24.dp else 16.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        ) {
            // Connected Button Group for navigation
            ButtonGroup(
                overflowIndicator = { state ->
                    IconButton(
                        onClick = { state.show() },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                },
                expandedRatio = 0.1f,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                // Previous button - Leading position
                clickableItem(
                    onClick = onPrevious,
                    label = if (isExpanded) "Previous" else "",
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                        )
                    },
                    enabled = uiState.hasPreviousQuestion,
                    weight = if (isExpanded) 1.2f else 0.8f,
                )

                // Question counter - Middle position
                customItem(
                    buttonGroupContent = {
                        Surface(
                            onClick = onJumpTo,
                            shape = ButtonGroupDefaults.connectedMiddleButtonShapes().shape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .sizeIn(minHeight = if (isExpanded) 48.dp else 40.dp)
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
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    },
                    menuContent = { menuState ->
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuState.isShowing,
                            onDismissRequest = { menuState.dismiss() }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("First Question") },
                                onClick = {
                                    menuState.dismiss()
                                    // Navigate to first
                                    repeat(uiState.currentQuestionIndex) { onPrevious() }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.KeyboardDoubleArrowLeft, contentDescription = null)
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Last Question") },
                                onClick = {
                                    menuState.dismiss()
                                    // Navigate to last
                                    repeat(uiState.totalQuestions - uiState.currentQuestionIndex - 1) { onNext() }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.KeyboardDoubleArrowRight, contentDescription = null)
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Jump to...") },
                                onClick = {
                                    menuState.dismiss()
                                    onJumpTo()
                                }
                            )
                        }
                    }
                )

                // Next button - Trailing position
                clickableItem(
                    onClick = onNext,
                    label = if (isExpanded) "Next" else "",
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                        )
                    },
                    enabled = uiState.hasNextQuestion,
                    weight = if (isExpanded) 1.2f else 0.8f,
                )
            }
        }
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
