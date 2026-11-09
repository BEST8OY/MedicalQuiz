package com.medqb.app.shared.ui.screens.history

import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.QuizSessionRepository
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoryListItem(
    entry: QuizSessionRepository.QuizSession,
    isSelected: Boolean,
    selectionModeEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeDelete: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectionModeEnabled) {
        if (selectionModeEnabled && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.shape),
        enableDismissFromStartToEnd = !selectionModeEnabled,
        enableDismissFromEndToStart = !selectionModeEnabled,
        gesturesEnabled = !selectionModeEnabled,
        onDismiss = { dismissValue ->
            when (dismissValue) {
                EndToStart -> onSwipeDelete()
                StartToEnd -> {
                    onRename()
                    scope.launch { dismissState.reset() }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            val dismissDirection = dismissState.dismissDirection

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                if (dismissDirection == StartToEnd) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 8.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 20.dp),
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Rename",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                } else if (dismissDirection == EndToStart) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 20.dp),
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Delete",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        },
    ) {
        val entryDisplayName = entry.displayName()

        val itemContent: @Composable () -> Unit = {
            Text(
                text = entryDisplayName,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }

        val itemSupportingContent: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entryDisplayName != entry.databaseName) {
                    Text(
                        text = entry.databaseName,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = "Q${entry.currentQuestionIndex + 1} \u00B7 ${relativeTimestamp(entry.updatedAtEpochMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        val itemLeadingContent: @Composable () -> Unit = {
            val motionScheme = MaterialTheme.motionScheme
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = selectionModeEnabled,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                ) { isSelectionMode ->
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        val accessibilityModifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (!selectionModeEnabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(label = "Delete") {
                            onSwipeDelete()
                            true
                        },
                        CustomAccessibilityAction(label = "Rename") {
                            onRename()
                            true
                        },
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = accessibilityModifier,
                content = itemContent,
                supportingContent = itemSupportingContent,
                leadingContent = itemLeadingContent,
            )
        }
    }
}

internal fun QuizSessionRepository.QuizSession.displayName(): String =
    entryName.ifBlank { databaseName }

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
