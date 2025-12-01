package com.medicalquiz.app.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.models.Subject
import com.medicalquiz.app.shared.data.models.System
import com.medicalquiz.app.shared.utils.Resource
import com.medicalquiz.app.shared.viewmodel.QuizViewModel
import kotlin.math.roundToInt

// ============================================================================
// Base Dialog Shell - Consistent container for all dialogs
// ============================================================================

@Composable
private fun DialogShell(
    onDismiss: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    ),
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = properties
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            content()
        }
    }
}

@Composable
private fun DialogHeader(
    title: String,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DialogActions(
    modifier: Modifier = Modifier,
    primaryText: String,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    destructive: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (secondaryText != null && onSecondary != null) {
            TextButton(onClick = onSecondary) {
                Text(secondaryText)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            colors = if (destructive) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) else ButtonDefaults.buttonColors()
        ) {
            Text(primaryText)
        }
    }
}

// ============================================================================
// Settings Dialog
// ============================================================================

@Composable
fun SettingsDialogComposable(
    isVisible: Boolean,
    viewModel: QuizViewModel,
    onDismiss: () -> Unit,
    onResetLogsRequested: () -> Unit
) {
    if (!isVisible) return
    
    val loggingEnabled = viewModel.settingsRepository?.isLoggingEnabled
        ?.collectAsStateWithLifecycle(false)?.value ?: false
    val showMetadata = viewModel.settingsRepository?.showMetadata
        ?.collectAsStateWithLifecycle(true)?.value ?: true
    val fontSize = viewModel.settingsRepository?.fontSize
        ?.collectAsStateWithLifecycle(16f)?.value ?: 16f

    SettingsDialog(
        initialLoggingEnabled = loggingEnabled,
        initialShowMetadata = showMetadata,
        initialFontSize = fontSize,
        onLoggingChanged = { viewModel.settingsRepository?.setLoggingEnabled(it) },
        onShowMetadataChanged = { viewModel.settingsRepository?.setShowMetadata(it) },
        onFontSizeChanged = { viewModel.settingsRepository?.setFontSize(it) },
        onResetLogs = {
            onDismiss()
            onResetLogsRequested()
        },
        onDismiss = onDismiss
    )
}

@Composable
private fun SettingsDialog(
    initialLoggingEnabled: Boolean,
    initialShowMetadata: Boolean,
    initialFontSize: Float,
    onLoggingChanged: (Boolean) -> Unit,
    onShowMetadataChanged: (Boolean) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onResetLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    var loggingEnabled by rememberSaveable(initialLoggingEnabled) { 
        mutableStateOf(initialLoggingEnabled) 
    }
    var showMetadata by rememberSaveable(initialShowMetadata) { 
        mutableStateOf(initialShowMetadata) 
    }
    var fontSize by rememberSaveable(initialFontSize) { 
        mutableFloatStateOf(initialFontSize) 
    }

    DialogShell(onDismiss = onDismiss) {
        Column {
            DialogHeader(
                title = "Settings",
                onClose = onDismiss
            )
            
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Answer Logging Toggle
                SettingsToggleRow(
                    title = "Answer logging",
                    description = "Track your progress and review history",
                    checked = loggingEnabled,
                    onCheckedChange = { enabled ->
                        loggingEnabled = enabled
                        onLoggingChanged(enabled)
                    }
                )
                
                // Show Metadata Toggle
                SettingsToggleRow(
                    title = "Show metadata",
                    description = "Display subject and system info after answering",
                    checked = showMetadata,
                    onCheckedChange = { visible ->
                        showMetadata = visible
                        onShowMetadataChanged(visible)
                    }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                // Font Size Slider
                FontSizeControl(
                    fontSize = fontSize,
                    onFontSizeChange = { size ->
                        fontSize = size
                        onFontSizeChanged(size)
                    }
                )
                
                // Reset Logs (only visible when logging is enabled)
                AnimatedVisibility(
                    visible = loggingEnabled,
                    enter = fadeIn() + scaleIn(initialScale = 0.95f),
                    exit = fadeOut() + scaleOut(targetScale = 0.95f)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onResetLogs),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clear log history",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Remove all saved answer logs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Done button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) },
        color = if (checked) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun FontSizeControl(
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Font size",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${fontSize.toInt()}sp",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { 
                    val newSize = (fontSize - 1f).coerceAtLeast(12f)
                    onFontSizeChange(newSize)
                },
                enabled = fontSize > 12f,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Slider(
                value = fontSize,
                onValueChange = onFontSizeChange,
                valueRange = 12f..24f,
                steps = 11,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            
            FilledTonalIconButton(
                onClick = { 
                    val newSize = (fontSize + 1f).coerceAtMost(24f)
                    onFontSizeChange(newSize)
                },
                enabled = fontSize < 24f,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        // Preview text
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                text = "Preview: The quick brown fox jumps over the lazy dog.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = fontSize.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// Jump To Dialog
// ============================================================================

@Composable
fun JumpToDialogComposable(
    isVisible: Boolean,
    totalQuestions: Int,
    currentIndex: Int,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible || totalQuestions <= 0) return

    JumpToQuestionDialog(
        totalQuestions = totalQuestions,
        currentQuestionIndex = currentIndex,
        onJumpTo = onJumpTo,
        onDismiss = onDismiss
    )
}

@Composable
private fun JumpToQuestionDialog(
    totalQuestions: Int,
    currentQuestionIndex: Int,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val clampedCurrent = (currentQuestionIndex + 1).coerceIn(1, totalQuestions)
    var inputValue by rememberSaveable(totalQuestions, currentQuestionIndex) {
        mutableStateOf(clampedCurrent.toString())
    }
    var sliderValue by rememberSaveable(totalQuestions, currentQuestionIndex) {
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
        Column {
            DialogHeader(
                title = "Jump to question",
                subtitle = "Currently on $clampedCurrent of $totalQuestions",
                onClose = onDismiss
            )
            
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
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
                    
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { value ->
                            inputValue = value.filter { it.isDigit() }.take(4)
                        },
                        modifier = Modifier
                            .width(100.dp)
                            .padding(horizontal = 16.dp),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
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
                                    onJumpTo(typedNumber!! - 1)
                                }
                            }
                        ),
                        singleLine = true,
                        isError = inputValue.isNotEmpty() && !isValid,
                        shape = RoundedCornerShape(16.dp),
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
                
                // Slider for quick navigation
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
                            steps = (totalQuestions - 2).coerceAtLeast(0),
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
                        onJumpTo(typedNumber!! - 1)
                    }
                },
                secondaryText = "Cancel",
                onSecondary = onDismiss
            )
        }
    }
}

// ============================================================================
// Error Dialog
// ============================================================================

@Composable
fun ErrorDialogComposable(
    errorDialog: Pair<String, String>?,
    onDismiss: () -> Unit
) {
    if (errorDialog == null) return
    
    val (title, message) = errorDialog

    DialogShell(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OK")
            }
        }
    }
}

// ============================================================================
// Reset Logs Confirmation Dialog
// ============================================================================

@Composable
fun ResetLogsConfirmationDialogComposable(
    isVisible: Boolean,
    activity: Any?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    DialogShell(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "Reset log history?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "This will permanently delete all stored answer logs. This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

// ============================================================================
// Performance Filter Dialog
// ============================================================================

@Composable
fun PerformanceFilterDialog(
    current: PerformanceFilter,
    onSelect: (PerformanceFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val filters = PerformanceFilter.entries
    var selected by remember { mutableStateOf(current) }

    DialogShell(onDismiss = onDismiss) {
        Column {
            DialogHeader(
                title = "Filter by performance",
                subtitle = "Show questions based on your history",
                onClose = onDismiss
            )
            
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filters) { filter ->
                    PerformanceFilterItem(
                        filter = filter,
                        isSelected = selected == filter,
                        onSelected = { selected = filter }
                    )
                }
            }
            
            DialogActions(
                primaryText = "Apply",
                onPrimary = {
                    onSelect(selected)
                    onDismiss()
                },
                secondaryText = "Cancel",
                onSecondary = onDismiss
            )
        }
    }
}

@Composable
private fun PerformanceFilterItem(
    filter: PerformanceFilter,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelected),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filter.displayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = filter.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            RadioButton(
                selected = isSelected,
                onClick = onSelected,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

private fun PerformanceFilter.displayName(): String = when (this) {
    PerformanceFilter.ALL -> "All Questions"
    PerformanceFilter.UNANSWERED -> "Not Attempted"
    PerformanceFilter.LAST_CORRECT -> "Last Correct"
    PerformanceFilter.LAST_INCORRECT -> "Last Incorrect"
    PerformanceFilter.EVER_CORRECT -> "Ever Correct"
    PerformanceFilter.EVER_INCORRECT -> "Ever Incorrect"
}

private fun PerformanceFilter.description(): String = when (this) {
    PerformanceFilter.ALL -> "Include all questions regardless of history"
    PerformanceFilter.UNANSWERED -> "Questions you haven't answered yet"
    PerformanceFilter.LAST_CORRECT -> "Your most recent attempt was correct"
    PerformanceFilter.LAST_INCORRECT -> "Your most recent attempt was incorrect"
    PerformanceFilter.EVER_CORRECT -> "Answered correctly at least once"
    PerformanceFilter.EVER_INCORRECT -> "Answered incorrectly at least once"
}

// ============================================================================
// Subject & System Filter Dialogs
// ============================================================================

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
        emptyMessage = "Select at least one subject first to see available systems.",
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

    DialogShell(
        onDismiss = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        AnimatedContent(
            targetState = resource,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            },
            label = "selection_dialog_content"
        ) { currentResource ->
            when (currentResource) {
                Resource.Loading -> SelectionLoadingContent(title = title, onDismiss = onDismiss)
                is Resource.Error -> SelectionErrorContent(
                    title = title,
                    message = currentResource.message ?: "Something went wrong.",
                    onRetry = onRetry,
                    onDismiss = onDismiss
                )
                is Resource.Success -> {
                    val data = currentResource.data
                    if (data.isEmpty()) {
                        SelectionEmptyContent(
                            title = title,
                            message = emptyMessage,
                            onDismiss = onDismiss
                        )
                    } else {
                        SelectionListContent(
                            title = title,
                            items = data,
                            selectedIds = selectedIds,
                            labelProvider = labelProvider,
                            idProvider = idProvider,
                            onApply = onApply,
                            onClear = onClear,
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionLoadingContent(
    title: String,
    onDismiss: () -> Unit
) {
    Column {
        DialogHeader(title = title, onClose = onDismiss)
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SelectionErrorContent(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        DialogHeader(title = title, onClose = onDismiss)
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(onClick = onDismiss) {
                    Text("Close")
                }
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun SelectionEmptyContent(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    Column {
        DialogHeader(title = title, onClose = onDismiss)
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun <T> SelectionListContent(
    title: String,
    items: List<T>,
    selectedIds: Set<Long>,
    labelProvider: (T) -> String,
    idProvider: (T) -> Long,
    onApply: (Set<Long>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentSelection by remember(selectedIds) { 
        mutableStateOf(selectedIds.toMutableSet()) 
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    val allIds = remember(items) { items.map { idProvider(it) }.toSet() }
    val isAllSelected = currentSelection.size == allIds.size && allIds.isNotEmpty()
    
    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { 
            labelProvider(it).contains(searchQuery, ignoreCase = true) 
        }
    }
    
    val listState = rememberLazyListState()

    Column {
        DialogHeader(
            title = title,
            subtitle = "${currentSelection.size} of ${items.size} selected",
            onClose = onDismiss
        )
        
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            placeholder = { Text("Search...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
        
        // Select all / Deselect all
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    currentSelection = if (isAllSelected) {
                        mutableSetOf()
                    } else {
                        allIds.toMutableSet()
                    }
                }
            ) {
                Icon(
                    imageVector = if (isAllSelected) Icons.Rounded.Close else Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isAllSelected) "Deselect all" else "Select all")
            }
            
            if (currentSelection.isNotEmpty()) {
                TextButton(
                    onClick = { 
                        currentSelection = mutableSetOf()
                    }
                ) {
                    Text("Clear")
                }
            }
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        // Item list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(
                items = filteredItems,
                key = { idProvider(it) }
            ) { item ->
                val itemId = idProvider(item)
                val isChecked = currentSelection.contains(itemId)
                
                SelectionItem(
                    label = labelProvider(item),
                    isChecked = isChecked,
                    onCheckedChange = { checked ->
                        currentSelection = currentSelection.toMutableSet().apply {
                            if (checked) add(itemId) else remove(itemId)
                        }
                    }
                )
            }
            
            if (filteredItems.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matches found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        DialogActions(
            primaryText = "Apply",
            onPrimary = { onApply(currentSelection.toSet()) },
            secondaryText = "Cancel",
            onSecondary = onDismiss
        )
    }
}

@Composable
private fun SelectionItem(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val backgroundColor = if (isChecked) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        Color.Transparent
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!isChecked) },
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
