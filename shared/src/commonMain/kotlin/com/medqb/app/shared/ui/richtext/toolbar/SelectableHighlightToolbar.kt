package com.medqb.app.shared.ui.richtext.toolbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.ui.richtext.RichTextTooltipContent
import com.medqb.app.shared.ui.richtext.styling.toSwatchColor

private object HighlightToolbarDefaults {
    val TouchTargetSize = 44.dp
    val SwatchDotSize = 28.dp
    val SwatchGap = 4.dp
    val ActionButtonHeight = 40.dp
    val MaxToolbarWidth = 280.dp
    val TonalElevation = 6.dp
    val ShadowElevation = 8.dp
}

@Immutable
internal sealed interface HighlightToolbarMode {
    val text: String

    data class Create(val selectedText: String) : HighlightToolbarMode {
        override val text: String get() = selectedText
    }

    data class Edit(
        val highlight: TextHighlight,
        val overlappingTooltip: RichTextTooltipContent? = null,
        val overlappingUrl: String? = null,
    ) : HighlightToolbarMode {
        override val text: String get() = highlight.highlightedText
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HighlightFloatingToolbar(
    mode: HighlightToolbarMode,
    onColorSelect: (HighlightColor) -> Unit,
    onCopy: () -> Unit,
    onLookup: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onOpenTooltip: (() -> Unit)? = null,
    onOpenLink: (() -> Unit)? = null,
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeColor = (mode as? HighlightToolbarMode.Edit)?.highlight?.color
    val hasText = mode.text.isNotBlank()

    Surface(
        modifier = Modifier.widthIn(max = HighlightToolbarDefaults.MaxToolbarWidth),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = HighlightToolbarDefaults.TonalElevation,
        shadowElevation = HighlightToolbarDefaults.ShadowElevation,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HighlightToolbarDefaults.SwatchGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HighlightColor.entries.forEach { color ->
                    HighlightColorDot(
                        color = color,
                        isSelected = color == activeColor,
                        isDarkTheme = isDarkTheme,
                        onClick = { onColorSelect(color) }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolbarActionChip(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy",
                    enabled = hasText,
                    onClick = onCopy
                )

                ToolbarActionChip(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = "Look up",
                    enabled = hasText,
                    onClick = onLookup
                )

                if (mode is HighlightToolbarMode.Edit && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(HighlightToolbarDefaults.ActionButtonHeight)
                            .clip(CircleShape),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete highlight",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (mode is HighlightToolbarMode.Edit && mode.overlappingTooltip != null && onOpenTooltip != null) {
                    ToolbarActionChip(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        label = "Definition",
                        enabled = true,
                        onClick = onOpenTooltip
                    )
                }

                if (mode is HighlightToolbarMode.Edit && mode.overlappingUrl != null && onOpenLink != null) {
                    ToolbarActionChip(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = "Open link",
                        enabled = true,
                        onClick = onOpenLink
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.height(HighlightToolbarDefaults.ActionButtonHeight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HighlightColorDot(
    color: HighlightColor,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val swatchColor = color.toSwatchColor(isDarkTheme)
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val colorLabel = "${color.displayName} highlight"

    Box(
        modifier = Modifier
            .size(HighlightToolbarDefaults.TouchTargetSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = HighlightToolbarDefaults.TouchTargetSize / 2),
                onClick = onClick
            )
            .semantics {
                contentDescription = if (isSelected) "$colorLabel, selected, tap to remove" else colorLabel
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(HighlightToolbarDefaults.SwatchDotSize)
                .clip(CircleShape)
                .background(swatchColor)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White
                )
            }
        }
    }
}
