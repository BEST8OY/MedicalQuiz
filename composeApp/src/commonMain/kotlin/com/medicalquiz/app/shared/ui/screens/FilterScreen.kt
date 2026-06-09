package com.medicalquiz.app.shared.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun FilterScreen(
    databaseName: String,
    subjectCount: Int,
    systemCount: Int,
    performanceLabel: String,
    previewCount: Int,
    isLoggingEnabled: Boolean,
    onLoggingToggle: (Boolean) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    onSelectSubjects: () -> Unit,
    onSelectSystems: () -> Unit,
    onSelectPerformance: () -> Unit,
    onStart: () -> Unit,
    onClearFilters: () -> Unit
) {
    val hasPreview = previewCount > 0
    val hasFilters = subjectCount > 0 || systemCount > 0 || performanceLabel != "All Questions"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    top = 40.dp,
                    end = 24.dp,
                    bottom = 40.dp + bottomContentPadding,
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    DatabaseHeaderCard(databaseName = databaseName)

                    FilterPreviewCard(previewCount = previewCount)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterSelectionCard(
                            title = "Subjects",
                            subtitle = if (subjectCount == 0) "All subjects" else "$subjectCount selected",
                            icon = Icons.Filled.Category,
                            isActive = subjectCount > 0,
                            onClick = onSelectSubjects
                        )

                        FilterSelectionCard(
                            title = "Systems",
                            subtitle = if (systemCount == 0) "All systems" else "$systemCount selected",
                            icon = Icons.Filled.FilterAlt,
                            isActive = systemCount > 0,
                            onClick = onSelectSystems
                        )

                        FilterSelectionCard(
                            title = "Performance",
                            subtitle = performanceLabel,
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            isActive = performanceLabel != "All Questions",
                            onClick = onSelectPerformance
                        )

                        LoggingToggleCard(
                            checked = isLoggingEnabled,
                            onCheckedChange = onLoggingToggle
                        )
                    }
                }

                PrimaryActionButtonGroup(
                    hasPreview = hasPreview,
                    hasFilters = hasFilters,
                    onStart = onStart,
                    onClearFilters = onClearFilters
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LoggingToggleCard(
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = iconContainerColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = iconColor
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Track Session Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (checked) contentColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Record answer logs for historical tracking",
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
private fun DatabaseHeaderCard(databaseName: String) {
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
private fun FilterPreviewCard(previewCount: Int) {
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
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
private fun FilterSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme

    val containerColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val iconContainerColor by animateColorAsState(
        targetValue = if (isActive) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val iconColor by animateColorAsState(
        targetValue = if (isActive) 
            MaterialTheme.colorScheme.onPrimary 
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = iconContainerColor,
                modifier = Modifier.size(44.dp)
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
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) contentColor else MaterialTheme.colorScheme.onSurface
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
private fun PrimaryActionButtonGroup(
    hasPreview: Boolean,
    hasFilters: Boolean,
    onStart: () -> Unit,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val controlsEnterEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val controlsExitEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

        Box(
            modifier = Modifier
                .width(320.dp)
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
        ) {
            AnimatedContent(
                targetState = hasFilters,
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    fadeIn(animationSpec = controlsEnterEffects)
                        .togetherWith(fadeOut(animationSpec = controlsExitEffects))
                },
                label = "filter_controls_swap",
            ) { showReset ->
                FilterActionControlButtonGroup(
                    hasPreview = hasPreview,
                    showReset = showReset,
                    onStart = onStart,
                    onClearFilters = onClearFilters,
                )
            }
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
    val groupModifier = if (showReset) Modifier.fillMaxWidth() else Modifier.width(176.dp)
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
            expandedRatio = ButtonGroupDefaults.ExpandedRatio,
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
                modifier = Modifier.size(20.dp),
            )
        },
        enabled = hasPreview,
        weight = 1.2f,
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
                modifier = Modifier.size(18.dp),
            )
        },
        weight = 1f,
    )
}
