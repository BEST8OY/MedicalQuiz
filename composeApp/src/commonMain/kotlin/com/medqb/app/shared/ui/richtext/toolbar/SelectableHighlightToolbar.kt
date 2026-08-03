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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.ui.theme.ElementSize
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
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(width = Stroke.Thin, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Inset.Md, vertical = Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: Action Controls (Copy & Dictionary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onCopy,
                    label = {
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(ElementSize.IconMd)
                        )
                    },
                    enabled = selectedText.isNotBlank(),
                    shape = MaterialTheme.shapes.medium,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.padding(vertical = 0.dp)
                )

                AssistChip(
                    onClick = onOpenExternal,
                    label = {
                        Text(
                            text = "Dictionary",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(ElementSize.IconMd)
                        )
                    },
                    enabled = selectedText.isNotBlank(),
                    shape = MaterialTheme.shapes.medium,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.padding(vertical = 0.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.9f),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Row 2: Highlight Color Swatches
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Md, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = Spacing.Xxs)
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
internal fun HighlightEditPopup(
    highlight: TextHighlight,
    onColorChange: (HighlightColor) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(width = Stroke.Thin, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Inset.Md, vertical = Spacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Md, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm, Alignment.CenterHorizontally),
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
                modifier = Modifier
                    .size(width = Stroke.Thin, height = 32.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(ElementSize.IconContainerMd)
                    .clip(MaterialTheme.shapes.medium),
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
            .size(38.dp)
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
                modifier = Modifier.size(ElementSize.IconMd),
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
