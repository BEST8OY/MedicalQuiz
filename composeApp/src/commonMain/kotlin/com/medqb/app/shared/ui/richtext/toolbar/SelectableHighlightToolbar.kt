package com.medqb.app.shared.ui.richtext

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.ui.theme.ContainerSize
import com.medqb.app.shared.ui.theme.HighlightToolbar
import com.medqb.app.shared.ui.theme.IconSize
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.ui.theme.Stroke

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelectionToolbar(
    selectedText: String,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    onHighlight: (HighlightColor) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = HighlightToolbar.TonalElevation,
        shadowElevation = HighlightToolbar.ShadowElevation,
        border = BorderStroke(width = Stroke.Thin, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(horizontal = Spacing.MediumSmall, vertical = Spacing.MediumSmall),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: Action Items (Copy & Dictionary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarActionItem(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy",
                    enabled = selectedText.isNotBlank(),
                    onClick = onCopy
                )

                ToolbarActionItem(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = "Dictionary",
                    enabled = selectedText.isNotBlank(),
                    onClick = onOpenExternal
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Spacing.Small),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Row 2: Color Swatches
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.MediumSmall, Alignment.CenterHorizontally),
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
private fun ToolbarActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val iconColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = contentColor,
        modifier = Modifier.clip(MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.MediumSmall, vertical = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SubSmall)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(IconSize.Medium)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
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
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = HighlightToolbar.TonalElevation,
        shadowElevation = HighlightToolbar.ShadowElevation,
        border = BorderStroke(width = Stroke.Thin, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Inset.Medium, vertical = Spacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.MediumSmall, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HighlightColor.entries.forEach { color ->
                    HighlightColorChip(
                        color = color,
                        isSelected = color == highlight.color,
                        onClick = { onColorChange(color) }
                    )
                }
            }

            VerticalDivider(
                modifier = Modifier.size(width = Stroke.Thin, height = HighlightToolbar.DividerHeight),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(ContainerSize.Medium)
                    .clip(MaterialTheme.shapes.medium),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete highlight",
                    modifier = Modifier.size(IconSize.Medium)
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
    val motionScheme = MaterialTheme.motionScheme

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "chipBorderColor"
    )
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isSelected) Stroke.Thick else Stroke.Thin,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "chipBorderWidth"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "chipScale"
    )

    Box(
        modifier = Modifier
            .size(HighlightToolbar.ColorChipSize)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(MaterialTheme.shapes.medium)
            .background(composeColor)
            .border(
                width = animatedBorderWidth,
                color = animatedBorderColor,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Medium),
                tint = contentColorFor(composeColor)
            )
        }
    }
}

@Composable
private fun contentColorFor(backgroundColor: Color): Color {
    return if (backgroundColor.luminance() > 0.5f) Color.Black else Color.White
}

internal fun HighlightColor.toComposeColor(): Color {
    val hex = this.hex.removePrefix("#")
    val colorLong = hex.toLongOrNull(16)
    return when {
        colorLong == null -> Color(0xFFFFEB3B)
        hex.length == 8 -> Color(
            alpha = ((colorLong shr 24) and 0xFF) / 255f,
            red = ((colorLong shr 16) and 0xFF) / 255f,
            green = ((colorLong shr 8) and 0xFF) / 255f,
            blue = (colorLong and 0xFF) / 255f
        )
        hex.length == 6 -> Color(
            red = ((colorLong shr 16) and 0xFF) / 255f,
            green = ((colorLong shr 8) and 0xFF) / 255f,
            blue = (colorLong and 0xFF) / 255f
        )
        hex.length == 3 -> Color(
            red = ((colorLong shr 8) and 0xF) * 0x11 / 255f,
            green = ((colorLong shr 4) and 0xF) * 0x11 / 255f,
            blue = (colorLong and 0xF) * 0x11 / 255f
        )
        hex.length == 4 -> Color(
            alpha = ((colorLong shr 12) and 0xF) * 0x11 / 255f,
            red = ((colorLong shr 8) and 0xF) * 0x11 / 255f,
            green = ((colorLong shr 4) and 0xF) * 0x11 / 255f,
            blue = (colorLong and 0xF) * 0x11 / 255f
        )
        else -> Color(0xFFFFEB3B)
    }
}
