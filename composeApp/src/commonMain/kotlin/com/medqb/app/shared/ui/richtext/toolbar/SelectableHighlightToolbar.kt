package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.ui.theme.ElementSize
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.ui.theme.Stroke

@Composable
internal fun SelectionToolbar(
    selectedText: String,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    onHighlight: (HighlightColor) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = Spacing.XxsPlus,
        shadowElevation = Spacing.Xxs,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.Xs, vertical = Spacing.XxsPlus),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.XxsPlus),
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.XxsPlus),
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
            modifier = Modifier.padding(horizontal = Spacing.Xs, vertical = Spacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.XxsPlus),
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
        tonalElevation = Spacing.XxsPlus,
        shadowElevation = Spacing.Xxs,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Xs, vertical = Spacing.XxsPlus),
            horizontalArrangement = Arrangement.spacedBy(Spacing.XxsPlus),
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
                modifier = Modifier.size(ElementSize.IconXl),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete highlight",
                    modifier = Modifier.size(ElementSize.IconMd)
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
    val composeColor = color.toComposeColor()
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isSelected) Stroke.Thick else Stroke.Thin

    Box(
        modifier = Modifier
            .size(Spacing.Xl)
            .graphicsLayer {
                clip = true
                shape = CircleShape
            }
            .drawBehind {
                drawCircle(color = composeColor)
            }
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}

internal fun HighlightColor.toComposeColor(): Color {
    val hex = this.hex.removePrefix("#")
    val colorLong = hex.toLongOrNull(16) ?: return Color.Yellow
    return Color(
        red = ((colorLong shr 16) and 0xFF) / 255f,
        green = ((colorLong shr 8) and 0xFF) / 255f,
        blue = (colorLong and 0xFF) / 255f
    )
}
