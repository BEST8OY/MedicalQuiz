package com.medicalquiz.app.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.models.Subject
import com.medicalquiz.app.shared.data.models.System
import com.medicalquiz.app.shared.utils.Resource
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import kotlin.math.roundToInt

// ============================================================================
// Wrapper Composables - Handle visibility and ViewModel integration
// ============================================================================

@Composable
fun SettingsDialogComposable(
    isVisible: Boolean,
    viewModel: QuizViewModel,
    onDismiss: () -> Unit,
    onResetLogsRequested: () -> Unit
) {
    if (isVisible) {
        val loggingEnabled = viewModel.settingsRepository?.isLoggingEnabled?.collectAsStateWithLifecycle(false)?.value ?: false
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { },
            text = {
                SettingsDialog(
                    initialLoggingEnabled = loggingEnabled,
                    onLoggingChanged = { enabled ->
                        viewModel.settingsRepository?.setLoggingEnabled(enabled)
                    },
                    onResetLogs = {
                        onDismiss()
                        onResetLogsRequested()
                    },
                    onDismiss = onDismiss
                )
            }
        )
    }
}

@Composable
fun JumpToDialogComposable(
    isVisible: Boolean,
    totalQuestions: Int,
    currentIndex: Int,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible && totalQuestions > 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { },
            text = {
                JumpToQuestionDialog(
                    totalQuestions = totalQuestions,
                    currentQuestionIndex = currentIndex,
                    onJumpTo = onJumpTo,
                    onDismiss = onDismiss
                )
            }
        )
    }
}

@Composable
fun ErrorDialogComposable(
    errorDialog: Pair<String, String>?,
    onDismiss: () -> Unit
) {
    if (errorDialog != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            },
            title = { Text(errorDialog.first) },
            text = { Text(errorDialog.second) }
        )
    }
}

@Composable
fun ResetLogsConfirmationDialogComposable(
    isVisible: Boolean,
    activity: Any?, // Removed AppCompatActivity dependency
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Reset log history") },
            text = { Text("This will permanently delete all stored answer logs. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    // Activity callback removed, logic should be handled by caller or ViewModel
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SubjectFilterDialogComposable(
    isVisible: Boolean,
    resource: Resource<List<Subject>>,
    selectedIds: Set<Long>,
    onRetry: () -> Unit,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionDialogHost(
        isVisible = isVisible,
        title = "Select subjects",
        resource = resource,
        emptyMessage = "No subjects available in this database.",
        selectedIds = selectedIds,
        labelProvider = { it.name },
        idProvider = { it.id },
        onRetry = onRetry,
        onApply = onApply,
        onClear = onClear,
        onDismiss = onDismiss
    )
}

@Composable
fun SystemFilterDialogComposable(
    isVisible: Boolean,
    resource: Resource<List<System>>,
    selectedIds: Set<Long>,
    onRetry: () -> Unit,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    SelectionDialogHost(
        isVisible = isVisible,
        title = "Select systems",
        resource = resource,
        emptyMessage = "Select at least one subject to browse systems.",
        selectedIds = selectedIds,
        labelProvider = { it.name },
        idProvider = { it.id },
        onRetry = onRetry,
        onApply = onApply,
        onClear = onClear,
        onDismiss = onDismiss
    )
}

// ============================================================================
// Dialog Content Implementations
// ============================================================================

@Composable
private fun JumpToQuestionDialog(
    totalQuestions: Int,
    currentQuestionIndex: Int,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val safeTotal = totalQuestions.takeIf { it > 0 } ?: 1
    val clampedCurrent = (currentQuestionIndex + 1).coerceIn(1, safeTotal)
    var inputValue by rememberSaveable(safeTotal, currentQuestionIndex) {
        mutableStateOf(clampedCurrent.toString())
    }
    var sliderValue by rememberSaveable(safeTotal, currentQuestionIndex) {
        mutableFloatStateOf(clampedCurrent.toFloat())
    }

    val typedNumber = inputValue.toIntOrNull()
    val isInRange = typedNumber != null && totalQuestions > 0 && typedNumber in 1..totalQuestions
    val supportingText = when {
        inputValue.isBlank() -> "Enter 1 to $safeTotal"
        typedNumber == null -> "Numbers only"
        !isInRange -> "Out of range"
        else -> null
    }

    LaunchedEffect(typedNumber, totalQuestions) {
        if (typedNumber != null && totalQuestions > 0) {
            val coerced = typedNumber.coerceIn(1, totalQuestions).toFloat()
            if (sliderValue != coerced) {
                sliderValue = coerced
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Jump to question",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Currently on $clampedCurrent of $safeTotal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { value ->
                        inputValue = value.filter { it.isDigit() }.take(4)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Question number") },
                    supportingText = supportingText?.let { { Text(it) } },
                    isError = supportingText != null && !isInRange,
                    singleLine = true
                )

                if (totalQuestions > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slider(
                            value = sliderValue,
                            onValueChange = { newValue ->
                                sliderValue = newValue
                                val snapped = newValue.roundToInt().coerceIn(1, totalQuestions)
                                inputValue = snapped.toString()
                            },
                            valueRange = 1f..totalQuestions.toFloat(),
                            steps = (totalQuestions - 2).coerceAtLeast(0)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (clampedCurrent > 1) {
                        SuggestionChip(
                            onClick = { inputValue = "1" },
                            label = { Text("First") }
                        )
                    }
                    if (clampedCurrent < safeTotal) {
                        SuggestionChip(
                            onClick = { inputValue = safeTotal.toString() },
                            label = { Text("Last") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (typedNumber != null && isInRange) {
                        onJumpTo(typedNumber - 1)
                    }
                },
                enabled = isInRange
            ) {
                Text("Jump")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



@Composable
private fun SettingsDialog(
    initialLoggingEnabled: Boolean,
    onLoggingChanged: (Boolean) -> Unit,
    onResetLogs: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    var loggingEnabled by rememberSaveable(initialLoggingEnabled) { mutableStateOf(initialLoggingEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Manage progress tracking",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Answer logging",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Track attempts and review progress",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = loggingEnabled,
                            onCheckedChange = { enabled ->
                                loggingEnabled = enabled
                                onLoggingChanged(enabled)
                            }
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Private") },
                            leadingIcon = {
                                Icon(Icons.Rounded.CheckCircle, null)
                            }
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("On-device") }
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("No sync") }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = loggingEnabled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FilledTonalButton(
                        onClick = onResetLogs,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Delete, null)
                        Text("Clear log history", modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Text(
                    text = "Data stays on your device only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun PerformanceFilterDialog(
    current: PerformanceFilter,
    onSelect: (PerformanceFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val filters = PerformanceFilter.values()
    var selected by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Filter by Performance",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { filter ->
                        PerformanceFilterItem(
                            filter = filter,
                            isSelected = selected == filter,
                            onSelected = { selected = filter }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelect(selected)
                    onDismiss()
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Apply", fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        },
        modifier = Modifier.fillMaxWidth(0.9f),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PerformanceFilterItem(
    filter: PerformanceFilter,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = filter.label(),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )

            RadioButton(
                selected = isSelected,
                onClick = onSelected,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun <T> SelectionDialogHost(
    isVisible: Boolean,
    title: String,
    resource: Resource<List<T>>,
    emptyMessage: String,
    selectedIds: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    onRetry: () -> Unit,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    when (resource) {
        Resource.Loading -> SelectionLoadingDialog(title = title, onDismiss = onDismiss)
        is Resource.Error -> SelectionErrorDialog(
            title = title,
            message = resource.message ?: "Something went wrong.",
            onRetry = onRetry,
            onDismiss = onDismiss
        )
        is Resource.Success -> {
            val data = resource.data
            if (data.isEmpty()) {
                SelectionEmptyDialog(title = title, message = emptyMessage, onDismiss = onDismiss)
            } else {
                GenericSelectionMenuDialog(
                    title = title,
                    items = data,
                    selectedIds = selectedIds,
                    labelProvider = labelProvider,
                    idProvider = idProvider,
                    onApply = onApply,
                    onCancel = onDismiss,
                    onClear = onClear,
                    showSelectAll = true
                )
            }
        }
    }
}

@Composable
private fun SelectionLoadingDialog(
    title: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text("Loading…")
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SelectionErrorDialog(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SelectionEmptyDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ============================================================================
// Shared helpers
// ============================================================================

private fun PerformanceFilter.label(): String = when (this) {
    PerformanceFilter.ALL -> "All Questions"
    PerformanceFilter.UNANSWERED -> "Not Attempted"
    PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
    PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
    PerformanceFilter.EVER_CORRECT -> "Ever Correct"
    PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
}
