package com.medqb.app.shared.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.QuizSessionRepository
import com.medqb.app.shared.data.database.DifficultyTier
import com.medqb.app.shared.ui.screens.filter.formatDifficultyLabel
import com.medqb.app.shared.ui.theme.IconSize
import com.medqb.app.shared.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoryItemCard(
    entry: QuizSessionRepository.QuizSession,
    isSelected: Boolean,
    selectionModeEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeDelete: () -> Unit,
    onSwipeRename: () -> Unit,
    onSelectChanged: () -> Unit,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectionModeEnabled) {
        if (selectionModeEnabled && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    val titleColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.shape)
            .semantics {
                contentDescription = "Swipe left to delete, right to rename"
            },
        enableDismissFromStartToEnd = !selectionModeEnabled,
        enableDismissFromEndToStart = !selectionModeEnabled,
        gesturesEnabled = !selectionModeEnabled,
        onDismiss = { dismissValue ->
            when (dismissValue) {
                EndToStart -> onSwipeDelete()
                StartToEnd -> {
                    onSwipeRename()
                    scope.launch { dismissState.reset() }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            val dismissDirection = dismissState.dismissDirection
            val backgroundColor by animateColorAsState(
                when (dismissDirection) {
                    EndToStart -> MaterialTheme.colorScheme.errorContainer
                    StartToEnd -> MaterialTheme.colorScheme.tertiaryContainer
                    SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surface
                }
            )
            val contentColor by animateColorAsState(
                when (dismissDirection) {
                    EndToStart -> MaterialTheme.colorScheme.onErrorContainer
                    StartToEnd -> MaterialTheme.colorScheme.onTertiaryContainer
                    SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.onSurface
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(horizontal = Spacing.MediumLarge),
            ) {
                Row(
                    modifier = Modifier
                        .align(
                            if (dismissDirection == EndToStart) {
                                Alignment.CenterEnd
                            } else {
                                Alignment.CenterStart
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (dismissDirection == EndToStart) {
                            Icons.Filled.Delete
                        } else {
                            Icons.Filled.Edit
                        },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                    Spacer(modifier = Modifier.width(Spacing.MediumSmall))
                    Text(
                        text = if (dismissDirection == EndToStart) "Delete" else "Rename",
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) {
        val entryDisplayName = entry.displayName()

        val itemContent: @Composable () -> Unit = {
            Text(
                text = entryDisplayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
        }

        val itemSupportingContent: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                if (entryDisplayName != entry.databaseName) {
                    Text(
                        text = entry.databaseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                }
                val difficultyLabel = if (entry.selectedDifficultyTiers.isNotEmpty() && entry.selectedDifficultyTiers.size < DifficultyTier.entries.size) {
                    " \u00B7 " + formatDifficultyLabel(entry.selectedDifficultyTiers)
                } else ""
                Text(
                    text = "Q${entry.currentQuestionIndex + 1}$difficultyLabel \u00B7 ${relativeTimestamp(entry.updatedAtEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                )
            }
        }

        val itemLeadingContent: @Composable () -> Unit = {
            val motionScheme = MaterialTheme.motionScheme
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = selectionModeEnabled,
                    enter = expandHorizontally(
                        animationSpec = motionScheme.defaultSpatialSpec(),
                        expandFrom = Alignment.Start
                    ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                    exit = shrinkHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        shrinkTowards = Alignment.Start
                    ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
                ) {
                    Box(modifier = Modifier.size(IconSize.Large)) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "Quiz history entry",
                    tint = iconTint,
                    modifier = Modifier.size(IconSize.Large),
                )
            }
        }

        if (selectionModeEnabled) {
            SegmentedListItem(
                selected = isSelected,
                onClick = onClick,
                onLongClick = onLongPress,
                shapes = shapes,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.Checkbox },
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                content = itemContent,
                supportingContent = itemSupportingContent,
                leadingContent = itemLeadingContent,
            )
        } else {
            SegmentedListItem(
                onClick = onClick,
                onLongClick = onLongPress,
                shapes = shapes,
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(),
                content = itemContent,
                supportingContent = itemSupportingContent,
                leadingContent = itemLeadingContent,
            )
        }
    }
}

internal fun QuizSessionRepository.QuizSession.displayName(): String =
    entryName.ifBlank { databaseName }

internal fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

internal fun Map<String, Int>.withIncrementedVersions(ids: Set<String>): Map<String, Int> =
    toMutableMap().apply {
        ids.forEach { id ->
            this[id] = (this[id] ?: 0) + 1
        }
    }

internal fun relativeTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Unknown"
    val now = Clock.System.now()
    val then = Instant.fromEpochMilliseconds(epochMillis)
    val diffMs = now.minus(then).inWholeMilliseconds
    val diffMin = diffMs / 1000 / 60
    val diffHr = diffMin / 60
    val diffDay = diffHr / 24
    return when {
        diffMin < 1 -> "Just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffHr < 24 -> "${diffHr}h ago"
        diffDay < 7 -> "${diffDay}d ago"
        else -> {
            val ldt = then.toLocalDateTime(TimeZone.currentSystemDefault())
            val mon = ldt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$mon ${ldt.day}, ${ldt.hour}:${ldt.minute.toString().padStart(2, '0')}"
        }
    }
}
