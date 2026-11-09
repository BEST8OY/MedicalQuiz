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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FilterToggleSegmentedItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme

    val iconContainerColor by animateColorAsState(
        targetValue = if (checked)
            MaterialTheme.colorScheme.secondary
        else
            MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "toggleItemIconContainerColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (checked)
            MaterialTheme.colorScheme.onSecondary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "toggleItemIconColor"
    )

    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = iconContainerColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Switch }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DatabaseHeaderCard(
    databaseName: String,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(40.dp)
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
internal fun FilterPreviewCard(
    previewCount: Int,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FilterSelectionSegmentedItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    index: Int,
    count: Int,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme

    val targetContainerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLowest
        isActive -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val targetContentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isActive -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val targetSupportingColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isActive -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val targetIconContainerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
        isActive -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val targetIconColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isActive -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "filterItemContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "filterItemContentColor"
    )

    val supportingColor by animateColorAsState(
        targetValue = targetSupportingColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "filterItemSupportingColor"
    )

    val iconContainerColor by animateColorAsState(
        targetValue = targetIconContainerColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "filterItemIconContainerColor"
    )

    val iconColor by animateColorAsState(
        targetValue = targetIconColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "filterItemIconColor"
    )

    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor,
            contentColor = contentColor,
            disabledContentColor = contentColor,
            supportingContentColor = supportingColor,
            disabledSupportingContentColor = supportingColor,
            leadingContentColor = contentColor,
            disabledLeadingContentColor = contentColor,
            trailingContentColor = supportingColor,
            disabledTrailingContentColor = supportingColor,
        ),
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = iconContainerColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = supportingColor,
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PrimaryActionButtonGroup(
    hasPreview: Boolean,
    hasFilters: Boolean,
    onStart: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
            contentAlignment = Alignment.Center,
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
    val groupModifier = if (showReset) Modifier.fillMaxWidth() else Modifier.widthIn(min = 176.dp).fillMaxWidth()
    val groupArrangement = if (showReset) {
        Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    } else {
        ButtonGroupDefaults.HorizontalArrangement
    }

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
                modifier = Modifier.size(20.dp),
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
                modifier = Modifier.size(20.dp),
            )
        },
        weight = 1f,
    )
}
