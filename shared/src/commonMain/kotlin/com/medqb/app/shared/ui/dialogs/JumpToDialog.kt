package com.medqb.app.shared.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.dialogs.components.DialogActions
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell
import kotlin.math.roundToInt

/**
 * Dialog for jumping to a specific question by number.
 *
 * Header and actions are pinned; only the number input + slider content scrolls.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JumpToDialog(
    totalQuestions: Int,
    currentIndex: Int,
    markedIndices: Set<Int> = emptySet(),
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (totalQuestions <= 0) return

    val sortedMarkedIndices = remember(markedIndices, totalQuestions) {
        markedIndices.filter { it in 0 until totalQuestions }.sorted()
    }

    val clampedCurrent = (currentIndex + 1).coerceIn(1, totalQuestions)
    var inputValue by rememberSaveable(totalQuestions, currentIndex) {
        mutableStateOf(clampedCurrent.toString())
    }
    var sliderValue by rememberSaveable(totalQuestions, currentIndex) {
        mutableFloatStateOf(clampedCurrent.toFloat())
    }

    val focusManager = LocalFocusManager.current

    val typedNumber = inputValue.toIntOrNull()
    val isValid = typedNumber != null && typedNumber in 1..totalQuestions
    val isTargetMarked = typedNumber != null && (typedNumber - 1) in markedIndices

    LaunchedEffect(typedNumber, totalQuestions) {
        if (typedNumber != null && typedNumber in 1..totalQuestions) {
            sliderValue = typedNumber.toFloat()
        }
    }

    DialogShell(onDismiss = onDismiss) {
        DialogHeader(
            title = "Jump to question",
            subtitle = "Currently on $clampedCurrent of $totalQuestions",
            onClose = onDismiss
        )

        // Scrollable content area — pinned header above, pinned actions below
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Number input with stepper buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val current = inputValue.toIntOrNull() ?: clampedCurrent
                            val newValue = (current - 1).coerceAtLeast(1)
                            inputValue = newValue.toString()
                        },
                        enabled = (inputValue.toIntOrNull() ?: clampedCurrent) > 1,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Rounded.Remove, "Decrease")
                    }

                    val maxDigits = totalQuestions.toString().length
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { value ->
                            inputValue = value.filter { it.isDigit() }.take(maxDigits)
                        },
                        modifier = Modifier
                            .width(120.dp)
                            .padding(horizontal = 12.dp),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (isValid) {
                                    onJumpTo(typedNumber - 1)
                                }
                            }
                        ),
                        singleLine = true,
                        isError = inputValue.isNotEmpty() && !isValid,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val current = inputValue.toIntOrNull() ?: clampedCurrent
                            val newValue = (current + 1).coerceAtMost(totalQuestions)
                            inputValue = newValue.toString()
                        },
                        enabled = (inputValue.toIntOrNull() ?: clampedCurrent) < totalQuestions,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Rounded.Add, "Increase")
                    }
                }

                if (sortedMarkedIndices.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isTargetMarked,
                            enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.testTag("jump_target_marked_badge")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Marked",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Slider for quick navigation
            if (totalQuestions > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sliderSteps = if (totalQuestions > 50) 0 else (totalQuestions - 2).coerceAtLeast(0)
                    val sliderState = rememberSliderState(
                        value = sliderValue,
                        steps = sliderSteps,
                        trackRange = 1f..totalQuestions.toFloat()
                    )
                    LaunchedEffect(sliderValue) {
                        sliderState.value = sliderValue
                    }
                    Slider(
                        state = sliderState,
                        onValueChange = { newValue ->
                            sliderValue = newValue
                            val snapped = newValue.roundToInt().coerceIn(1, totalQuestions)
                            inputValue = snapped.toString()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalQuestions.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick-jump strip for marked questions
            if (sortedMarkedIndices.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Marked in session (${sortedMarkedIndices.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val nextMarkedIndex = sortedMarkedIndices.firstOrNull { it > currentIndex }
                            ?: sortedMarkedIndices.first()
                        val canJumpNext = nextMarkedIndex != currentIndex
                        TextButton(
                            onClick = { onJumpTo(nextMarkedIndex) },
                            enabled = canJumpNext,
                            modifier = Modifier.testTag("jump_next_marked_button")
                        ) {
                            Text(
                                text = "Next marked",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(sortedMarkedIndices, key = { it }) { index ->
                            val isCurrent = index == currentIndex
                            AssistChip(
                                onClick = { if (isCurrent) onDismiss() else onJumpTo(index) },
                                label = { Text("#${index + 1}") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Flag,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.testTag("jump_marked_chip_${index + 1}")
                            )
                        }
                    }
                }
            }
        }

        DialogActions(
            primaryText = "Jump",
            primaryEnabled = isValid,
            onPrimary = {
                if (isValid) {
                    onJumpTo(typedNumber - 1)
                }
            },
            secondaryText = "Cancel",
            onSecondary = onDismiss
        )
    }
}
