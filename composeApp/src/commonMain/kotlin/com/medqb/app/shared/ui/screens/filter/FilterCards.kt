package com.medqb.app.shared.ui.screens.filter

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.medqb.app.shared.ui.theme.ContainerSize
import com.medqb.app.shared.ui.theme.IconSize
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ToggleCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = if (checked)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val contentColor by animateColorAsState(
        targetValue = if (checked)
            MaterialTheme.colorScheme.onSecondaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val iconContainerColor by animateColorAsState(
        targetValue = if (checked)
            MaterialTheme.colorScheme.secondary
        else
            MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val iconColor by animateColorAsState(
        targetValue = if (checked)
            MaterialTheme.colorScheme.onSecondary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    Card(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Inset.Medium, vertical = Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = iconContainerColor,
                modifier = Modifier.size(ContainerSize.Large)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DatabaseHeaderCard(databaseName: String) {
    val motionScheme = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.MediumLarge, vertical = Spacing.Large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(ContainerSize.Medium)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "QBank",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = databaseName.ifEmpty { "Unknown" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FilterPreviewCard(previewCount: Int) {
    val hasPreview = previewCount > 0
    val statusText = when {
        previewCount > 1 -> "$previewCount questions available"
        previewCount == 1 -> "1 question available"
        else -> "No matching questions"
    }
    val supportingText = if (hasPreview) {
        "Tap Start to begin your quiz session."
    } else {
        "Try adjusting your filters to find questions."
    }

    val motionScheme = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = if (hasPreview)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Inset.Large, vertical = Spacing.MediumLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.MediumSmall)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasPreview) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasPreview) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FilterSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme

    val containerColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.tertiaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.onTertiaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val iconContainerColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.tertiary
        else
            MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val iconColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.onTertiary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Inset.Medium, vertical = Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = iconContainerColor,
                modifier = Modifier.size(ContainerSize.Large)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PrimaryActionButtonGroup(
    hasPreview: Boolean,
    hasFilters: Boolean,
    onStart: () -> Unit,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.MediumSmall),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = Layout.MaxContentWidth)
                .fillMaxWidth()
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
        ) {
            FilterActionControlButtonGroup(
                hasPreview = hasPreview,
                showReset = hasFilters,
                onStart = onStart,
                onClearFilters = onClearFilters,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterActionControlButtonGroup(
    hasPreview: Boolean,
    showReset: Boolean,
    onStart: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val groupModifier = if (showReset) Modifier.fillMaxWidth() else Modifier.widthIn(min = Layout.SingleButtonMinWidth).fillMaxWidth()
    val groupArrangement = if (showReset) {
        Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    } else {
        ButtonGroupDefaults.HorizontalArrangement
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ButtonGroup(
            overflowIndicator = {
                ButtonGroupDefaults.OverflowIndicator(menuState = it)
            },
            modifier = groupModifier,
            horizontalArrangement = groupArrangement,
            expandedRatio = 0f,
        ) {
            StartQuizButtonGroupItem(
                hasPreview = hasPreview,
                onStart = onStart,
            )

            if (showReset) {
                ResetFiltersButtonGroupItem(onClearFilters = onClearFilters)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.StartQuizButtonGroupItem(
    hasPreview: Boolean,
    onStart: () -> Unit,
) {
    clickableItem(
        onClick = onStart,
        label = if (hasPreview) "Start Quiz" else "No matches",
        icon = {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Medium),
            )
        },
        enabled = hasPreview,
        weight = 1.5f,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.ResetFiltersButtonGroupItem(
    onClearFilters: () -> Unit,
) {
    clickableItem(
        onClick = onClearFilters,
        label = "Reset",
        icon = {
            Icon(
                imageVector = Icons.Filled.FilterAltOff,
                contentDescription = null,
                modifier = Modifier.size(IconSize.Medium),
            )
        },
        weight = 1f,
    )
}
