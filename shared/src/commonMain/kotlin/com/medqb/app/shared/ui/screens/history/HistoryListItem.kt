package com.medqb.app.shared.ui.screens.history

import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.QuizSessionRepository
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch
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

    LaunchedEffect(entry.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    LaunchedEffect(selectionModeEnabled) {
        if (selectionModeEnabled && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    val renameContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    val onRenameContainerColor = MaterialTheme.colorScheme.onTertiaryContainer
    val deleteContainerColor = MaterialTheme.colorScheme.errorContainer
    val onDeleteContainerColor = MaterialTheme.colorScheme.onErrorContainer

    val outerClipPath = remember { Path() }
    val startSlicePath = remember { Path() }
    val endSlicePath = remember { Path() }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .clipDuringSwipe(
                dismissState = dismissState,
                shape = shapes.shape,
                reusePath = outerClipPath,
            ),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {},
            ) {
                // StartToEnd (Rename) pill pocket: strictly confined to [0, dx - gap]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawSwipeActionPill(
                            dismissState = dismissState,
                            isStartToEnd = true,
                            containerColor = renameContainerColor,
                            shape = shapes.shape,
                            reusePath = startSlicePath,
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = onRenameContainerColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Rename",
                            color = onRenameContainerColor,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                // EndToStart (Delete) pill pocket: strictly confined to [W - (|dx| - gap), W]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawSwipeActionPill(
                            dismissState = dismissState,
                            isStartToEnd = false,
                            containerColor = deleteContainerColor,
                            shape = shapes.shape,
                            reusePath = endSlicePath,
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = onDeleteContainerColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Delete",
                            color = onDeleteContainerColor,
                            style = MaterialTheme.typography.labelLarge,
                        )
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

/**
 * Safely reads the current drag offset from [SwipeToDismissBoxState], returning `0f` before
 * the underlying [androidx.compose.foundation.gestures.AnchoredDraggableState] anchors are
 * initialized on the first layout pass.
 */
private fun SwipeToDismissBoxState.safeOffset(): Float =
    try {
        val value = requireOffset()
        if (value.isNaN()) 0f else value
    } catch (_: IllegalStateException) {
        0f
    }

/**
 * Clips the outer [SwipeToDismissBox] silhouette to [shape] only while a swipe is actively
 * displaced (`placedOffsetPx != 0`), preventing the translating card from overflowing into
 * parent padding while avoiding double-clipping when settled at rest.
 */
private fun Modifier.clipDuringSwipe(
    dismissState: SwipeToDismissBoxState,
    shape: Shape,
    reusePath: Path,
): Modifier = drawWithContent {
    val rawOffset = dismissState.safeOffset()
    if (rawOffset.roundToInt() == 0) {
        drawContent()
        return@drawWithContent
    }

    reusePath.rewind()
    reusePath.addOutline(shape.createOutline(size, layoutDirection, this))
    clipPath(reusePath) {
        this@drawWithContent.drawContent()
    }
}

/**
 * Draws the swipe action background and icon/label inside a dynamic rounded pill of [shape]
 * that grows within the exposed gap left behind by the sliding [SegmentedListItem].
 *
 * Because the pill never extends into the `[dx, W]` (or `[0, W - |dx|]`) region occupied by the
 * card, `SegmentedListItem` retains its native [ListItemShapes] with zero background bleeding.
 */
private fun Modifier.drawSwipeActionPill(
    dismissState: SwipeToDismissBoxState,
    isStartToEnd: Boolean,
    containerColor: Color,
    shape: Shape,
    reusePath: Path,
): Modifier = drawWithContent {
    val rawOffset = dismissState.safeOffset()
    val isActiveDirection = if (isStartToEnd) rawOffset > 0f else rawOffset < 0f
    if (!isActiveDirection || size.width <= 0f) return@drawWithContent

    val rtlModifier = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val placedOffsetPx = (rawOffset * rtlModifier).roundToInt().toFloat()
    val absOffsetPx = abs(placedOffsetPx)
    if (absOffsetPx <= 0f) return@drawWithContent

    // Maintain a crisp 2.dp separation gap between the action pill and the sliding card,
    // tapering smoothly to 0px as the item reaches full dismissal.
    val fraction = (absOffsetPx / size.width).coerceIn(0f, 1f)
    val gapPx = 2.dp.toPx() * (1f - fraction)
    val revealWidth = (absOffsetPx - gapPx).coerceIn(0f, size.width)
    if (revealWidth <= 1f) return@drawWithContent

    val sliceLeft = if (placedOffsetPx > 0f) {
        0f
    } else {
        (size.width - revealWidth).coerceAtLeast(0f)
    }
    val sliceSize = Size(revealWidth, size.height)

    reusePath.rewind()
    reusePath.addOutline(shape.createOutline(sliceSize, layoutDirection, this))
    if (sliceLeft != 0f) {
        reusePath.translate(Offset(sliceLeft, 0f))
    }

    clipPath(reusePath) {
        drawRect(
            color = containerColor,
            topLeft = Offset(sliceLeft, 0f),
            size = sliceSize,
        )
        this@drawWithContent.drawContent()
    }
}

internal fun QuizSessionRepository.QuizSession.displayName(): String =
    entryName.ifBlank { databaseName }

internal fun relativeTimestamp(epochMillis: Long, now: Instant = Clock.System.now()): String {
    if (epochMillis <= 0L) return "Unknown"
    val then = Instant.fromEpochMilliseconds(epochMillis)
    val duration = now - then
    val diffMin = duration.inWholeMinutes
    val diffHr = duration.inWholeHours
    val diffDay = duration.inWholeDays
    return when {
        diffMin < 1 -> "Just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffHr < 24 -> "${diffHr}h ago"
        diffDay < 7 -> "${diffDay}d ago"
        else -> {
            val ldt = then.toLocalDateTime(TimeZone.currentSystemDefault())
            val mon = ldt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val hourStr = ldt.hour.toString().padStart(2, '0')
            val minStr = ldt.minute.toString().padStart(2, '0')
            "$mon ${ldt.day}, $hourStr:$minStr"
        }
    }
}
