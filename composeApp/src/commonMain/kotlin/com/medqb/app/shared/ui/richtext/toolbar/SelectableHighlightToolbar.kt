package com.medqb.app.shared.ui.richtext

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.ui.theme.ElementSize
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.ui.theme.Stroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionToolbar(
    selectedText: String,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    onHighlight: (HighlightColor) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onCopy,
                    label = { androidx.compose.material3.Text("Copy") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(ElementSize.IconMd)
                        )
                    },
                    enabled = selectedText.isNotBlank(),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                AssistChip(
                    onClick = onOpenExternal,
                    label = { androidx.compose.material3.Text("Dictionary") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(ElementSize.IconMd)
                        )
                    },
                    enabled = selectedText.isNotBlank(),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HighlightEditPopup(
    highlight: TextHighlight,
    onColorChange: (HighlightColor) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HighlightColor.entries.forEach { color ->
                HighlightColorChip(
                    color = color,
                    isSelected = color == highlight.color,
                    onClick = { onColorChange(color) }
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .size(width = Stroke.Thin, height = Layout.MinTouchTarget)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(Layout.MinTouchTarget),
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
    val motionScheme = MaterialTheme.motionScheme

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
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
        targetValue = if (isSelected) 1.1f else 1.0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "chipScale"
    )

    Box(
        modifier = Modifier
            .size(ElementSize.IconContainerMd)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(CircleShape)
            .background(composeColor)
            .border(
                width = animatedBorderWidth,
                color = animatedBorderColor,
                shape = CircleShape
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
                modifier = Modifier.size(ElementSize.IconSm),
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
        colorLong == null -> Color(0xFFFFEB3B) // HighlightColor.YELLOW fallback
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
        // 3-char shorthand: #RGB → #RRGGBB
        hex.length == 3 -> Color(
            red = ((colorLong shr 8) and 0xF) * 0x11 / 255f,
            green = ((colorLong shr 4) and 0xF) * 0x11 / 255f,
            blue = (colorLong and 0xF) * 0x11 / 255f
        )
        // 4-char shorthand: #ARGB → #AARRGGBB
        hex.length == 4 -> Color(
            alpha = ((colorLong shr 12) and 0xF) * 0x11 / 255f,
            red = ((colorLong shr 8) and 0xF) * 0x11 / 255f,
            green = ((colorLong shr 4) and 0xF) * 0x11 / 255f,
            blue = (colorLong and 0xF) * 0x11 / 255f
        )
        else -> Color(0xFFFFEB3B) // HighlightColor.YELLOW fallback
    }
}
