package com.medicalquiz.app.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.data.database.PerformanceFilter
import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.models.System
import com.medicalquiz.app.utils.Resource
import com.medicalquiz.app.viewmodel.QuizViewModel
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
                        if (!enabled) viewModel.clearPendingLogsBuffer()
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
    activity: AppCompatActivity?,
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
                    (activity as? com.medicalquiz.app.QuizActivity)?.onResetLogsConfirmed()
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
        inputValue.isBlank() -> "Enter a number from 1 to $safeTotal"
        typedNumber == null -> "Only digits are allowed"
        !isInRange -> "Value must stay within 1 and $safeTotal"
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

    DialogSurface(maxWidth = 420.dp, verticalSpacing = 20.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Jump to question",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "You are on $clampedCurrent of $safeTotal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close dialog"
                    )
                }
            }

            OutlinedTextField(
                value = inputValue,
                onValueChange = { value ->
                    inputValue = value.filter { it.isDigit() }.take(4)
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                label = { Text("Question number") },
                supportingText = supportingText?.let { helper ->
                    { Text(text = helper, style = MaterialTheme.typography.bodySmall) }
                },
                isError = supportingText != null && !isInRange,
                singleLine = true
            )

            if (totalQuestions > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            sliderValue = newValue
                            val snapped = newValue.roundToInt().coerceIn(1, totalQuestions)
                            inputValue = snapped.toString()
                        },
                        valueRange = 1f..totalQuestions.toFloat(),
                        steps = (totalQuestions - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                    LinearProgressIndicator(
                        progress = sliderValue / safeTotal.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickJumpChip(label = "Current", value = clampedCurrent, onSelect = { inputValue = it })
                if (clampedCurrent > 1) {
                    QuickJumpChip(label = "Prev", value = (clampedCurrent - 1).coerceAtLeast(1), onSelect = { inputValue = it })
                }
                if (clampedCurrent < safeTotal) {
                    QuickJumpChip(label = "Next", value = (clampedCurrent + 1).coerceAtMost(safeTotal), onSelect = { inputValue = it })
                }
                QuickJumpChip(label = "First", value = 1, onSelect = { inputValue = it })
                QuickJumpChip(label = "Last", value = safeTotal, onSelect = { inputValue = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                FilledTonalButton(
                    onClick = {
                        if (typedNumber != null && isInRange) {
                            onJumpTo(typedNumber - 1)
                            onDismiss()
                        }
                    },
                    enabled = isInRange,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Jump")
                }
            }
    }
}

@Composable
private fun QuickJumpChip(
    label: String,
    value: Int,
    onSelect: (String) -> Unit
) {
    SuggestionChip(
        onClick = { onSelect(value.toString()) },
        label = {
            Text(
                text = "$label $value",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge
            )
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
    var loggingEnabled by rememberSaveable { mutableStateOf(initialLoggingEnabled) }

    DialogSurface(maxWidth = 480.dp, verticalSpacing = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Manage how progress is tracked on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close dialog"
                    )
                }
            }

            ElevatedCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ListItem(
                        headlineContent = {
                            Text("Answer logging", fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text("Collects attempt history so you can review weak areas later.")
                        },
                        trailingContent = {
                            Switch(
                                checked = loggingEnabled,
                                onCheckedChange = { enabled ->
                                    loggingEnabled = enabled
                                    onLoggingChanged(enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                    checkedThumbColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Private storage") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                leadingIconContentColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("On this device") }
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("No cloud sync") }
                        )
                    }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Clear log history")
                        Icon(imageVector = Icons.Rounded.Delete, contentDescription = null)
                    }
                }
            }

            Text(
                text = "All insights stay on-device. You can turn logging off at any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
    }
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

@Composable
private fun DialogSurface(
    maxWidth: Dp,
    verticalSpacing: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = maxWidth)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content
        )
    }
}

private fun PerformanceFilter.label(): String = when (this) {
    PerformanceFilter.ALL -> "All Questions"
    PerformanceFilter.UNANSWERED -> "Not Attempted"
    PerformanceFilter.LAST_CORRECT -> "Last Attempt Correct"
    PerformanceFilter.LAST_INCORRECT -> "Last Attempt Incorrect"
    PerformanceFilter.EVER_CORRECT -> "Ever Correct"
    PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
}
