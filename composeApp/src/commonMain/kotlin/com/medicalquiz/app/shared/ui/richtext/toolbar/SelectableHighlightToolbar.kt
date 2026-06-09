package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.models.HighlightColor
import com.medicalquiz.app.shared.data.models.TextHighlight
import com.medicalquiz.app.shared.ui.theme.HighlightTheme
import com.medicalquiz.app.shared.ui.theme.toContainerColors

@Composable
internal fun SelectionToolbar(
    selectedText: String,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    onHighlight: (HighlightColor) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarActionButton(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy",
                    enabled = selectedText.isNotBlank(),
                    onClick = onCopy
                )
                ToolbarActionButton(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = "Dictionary",
                    enabled = selectedText.isNotBlank(),
                    onClick = onOpenExternal
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HighlightColor.entries.forEach { color ->
                    HighlightColorChip(
                        color = color,
                        isSelected = false,
                        onClick = { onHighlight(color) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
internal fun HighlightEditPopup(
    highlight: TextHighlight,
    onColorChange: (HighlightColor) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HighlightColor.entries.forEach { color ->
                HighlightColorChip(
                    color = color,
                    isSelected = color == highlight.color,
                    onClick = { onColorChange(color) }
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete highlight",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun HighlightColorChip(
    color: HighlightColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scheme = HighlightTheme.colors
    val (containerColor, onContainerColor) = color.toContainerColors(scheme)
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        containerColor
    }

    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = onContainerColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .size(32.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.small
            )
    ) {
        Text(
            text = color.displayName.take(1),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(4.dp),
            color = onContainerColor
        )
    }
}
