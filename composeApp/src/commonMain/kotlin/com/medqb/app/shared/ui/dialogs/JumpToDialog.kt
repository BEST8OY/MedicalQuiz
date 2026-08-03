package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.dialogs.components.DialogActions
import com.medqb.app.shared.ui.dialogs.components.DialogHeader
import com.medqb.app.shared.ui.dialogs.components.DialogShell
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Dialog for jumping to a specific question by number.
 */
@Composable
fun JumpToDialog(
    totalQuestions: Int,
    currentIndex: Int,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (totalQuestions <= 0) return

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

    LaunchedEffect(typedNumber, totalQuestions) {
        if (typedNumber != null && typedNumber in 1..totalQuestions) {
            sliderValue = typedNumber.toFloat()
        }
    }

    DialogShell(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            DialogHeader(
                title = "Jump to question",
                subtitle = "Currently on $clampedCurrent of $totalQuestions",
                onClose = onDismiss
            )

            Column(
                modifier = Modifier.padding(horizontal = Inset.Lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.LgSm)
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
                        modifier = Modifier.size(Layout.MinTouchTarget)
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
                            .padding(horizontal = Spacing.Sm),
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
                        modifier = Modifier.size(Layout.MinTouchTarget)
                    ) {
                        Icon(Icons.Rounded.Add, "Increase")
                    }
                }

                // Slider for quick navigation
                if (totalQuestions > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                        val sliderSteps = if (totalQuestions > 50) 0 else (totalQuestions - 2).coerceAtLeast(0)
                        Slider(
                            value = sliderValue,
                            onValueChange = { newValue ->
                                sliderValue = newValue
                                val snapped = newValue.roundToInt().coerceIn(1, totalQuestions)
                                inputValue = snapped.toString()
                            },
                            valueRange = 1f..totalQuestions.toFloat(),
                            steps = sliderSteps,
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
}
