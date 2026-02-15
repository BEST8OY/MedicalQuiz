package com.medicalquiz.app.shared.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.FontScalePresets
import com.medicalquiz.app.shared.ui.dialogs.components.DialogHeader
import com.medicalquiz.app.shared.ui.dialogs.components.DialogShell

/**
 * Settings dialog with logging, metadata, and font size options.
 */
@Composable
fun SettingsDialog(
    isVisible: Boolean,
    initialLoggingEnabled: Boolean,
    initialShowMetadata: Boolean,
    initialFontScalePreference: Float?,
    onLoggingChanged: (Boolean) -> Unit,
    onShowMetadataChanged: (Boolean) -> Unit,
    onFontScalePreferenceChanged: (Float?) -> Unit,
    onResetLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    var loggingEnabled by rememberSaveable(initialLoggingEnabled) {
        mutableStateOf(initialLoggingEnabled)
    }
    var showMetadata by rememberSaveable(initialShowMetadata) {
        mutableStateOf(initialShowMetadata)
    }
    var selectedFontOption by rememberSaveable(initialFontScalePreference) {
        mutableStateOf(FontScaleOption.fromScale(initialFontScalePreference))
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

                FontScaleControl(
                    selected = selectedFontOption,
                    onSelected = { option ->
                        selectedFontOption = option
                        onFontScalePreferenceChanged(option.scale)
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
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onResetLogs),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Remove all saved answer logs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) },
        color = if (checked)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FontScaleControl(
    selected: FontScaleOption,
    onSelected: (FontScaleOption) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Reading text size",
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )

        FontScaleOption.entries.forEach { option ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSelected(option) },
                color = if (selected == option) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == option,
                        onClick = { onSelected(option) },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        option.description?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = "Applies to question and media description rich text only.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class FontScaleOption(
    val label: String,
    val scale: Float?,
    val description: String? = null,
) {
    FollowSystem("Follow system", null),
    Compact("Compact", FontScalePresets.COMPACT, "0.9×"),
    Default("Default", FontScalePresets.DEFAULT, "1.0×"),
    Large("Large", FontScalePresets.LARGE, "1.15×"),
    ExtraLarge("Extra large", FontScalePresets.EXTRA_LARGE, "1.3×"),
    ;

    companion object {
        fun fromScale(scale: Float?): FontScaleOption =
            entries.firstOrNull { option -> FontScalePresets.matches(option.scale, scale) } ?: FollowSystem
    }
}
