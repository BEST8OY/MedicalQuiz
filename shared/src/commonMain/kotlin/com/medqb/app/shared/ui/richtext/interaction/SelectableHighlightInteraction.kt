package com.medqb.app.shared.ui.richtext.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.ui.richtext.RichTextTooltipContent

@Immutable
internal data class TextSelectionState(
    val isSelecting: Boolean = false,
    val isDragging: Boolean = false,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val selectedText: String = "",
    val isMouseSelection: Boolean = false,
) {
    val hasSelectionRange: Boolean get() = isSelecting && endOffset > startOffset
    val showSelectionToolbar: Boolean get() = hasSelectionRange && !isDragging
    val selectionRange: IntRange get() = startOffset until endOffset
}

internal data class HighlightTapResult(
    val highlight: TextHighlight,
    val overlappingTooltip: RichTextTooltipContent? = null,
    val overlappingUrl: String? = null,
)

@Composable
internal fun Modifier.selectableHighlightGestures(
    text: AnnotatedString,
    highlights: List<TextHighlight>,
    longPressDragHysteresisPx: Float,
    currentLayoutResult: () -> TextLayoutResult?,
    currentSelectionState: () -> TextSelectionState,
    setSelectionState: (TextSelectionState) -> Unit,
    setEditingHighlight: (HighlightTapResult?) -> Unit,
    setDragPosition: (Offset) -> Unit,
    onDismissAll: () -> Unit,
    onLinkClick: ((String) -> Unit)?,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
): Modifier {
    val currentHighlights by rememberUpdatedState(highlights)
    val currentHysteresisPx by rememberUpdatedState(longPressDragHysteresisPx)
    val latestLayoutResult by rememberUpdatedState(currentLayoutResult)
    val latestSelectionState by rememberUpdatedState(currentSelectionState)
    val currentSetSelectionState by rememberUpdatedState(setSelectionState)
    val currentSetEditingHighlight by rememberUpdatedState(setEditingHighlight)
    val currentSetDragPosition by rememberUpdatedState(setDragPosition)
    val currentOnDismissAll by rememberUpdatedState(onDismissAll)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnTooltipClick by rememberUpdatedState(onTooltipClick)
    val haptic = LocalHapticFeedback.current

    return pointerInput(text) {
        var lastMouseClickTimeMs = -1L
        var lastMouseClickPos = Offset.Zero

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            if (text.isEmpty()) return@awaitEachGesture

            val isMouse = down.type == PointerType.Mouse
            if (isMouse) {
                val layout = latestLayoutResult() ?: return@awaitEachGesture
                val downPos = down.position
                val anchorOffset = layout.getOffsetForPosition(downPos).coerceIn(0, text.length)
                var didDrag = false
                var currentFocusOffset = anchorOffset
                var completedNormally = false

                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            completedNormally = true
                            if (didDrag) {
                                change.consume()
                            } else if (!change.isConsumed) {
                                val handled = handleTapOrMouseDoubleClick(
                                    tapPosition = change.position,
                                    uptimeMillis = change.uptimeMillis,
                                    isMouse = true,
                                    lastMouseClickTimeMs = lastMouseClickTimeMs,
                                    lastMouseClickPos = lastMouseClickPos,
                                    doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis,
                                    doubleTapSlopPx = viewConfiguration.touchSlop * 2f,
                                    updateLastClick = { t, p ->
                                        lastMouseClickTimeMs = t
                                        lastMouseClickPos = p
                                    },
                                    text = text,
                                    layout = layout,
                                    highlights = currentHighlights,
                                    setSelectionState = currentSetSelectionState,
                                    setEditingHighlight = currentSetEditingHighlight,
                                    onDismissAll = currentOnDismissAll,
                                    onLinkClick = currentOnLinkClick,
                                    onTooltipClick = currentOnTooltipClick,
                                )
                                if (handled) {
                                    change.consume()
                                }
                            }
                            break
                        }

                        val movedDist = (change.position - downPos).getDistance()
                        if (!didDrag && movedDist >= viewConfiguration.touchSlop) {
                            didDrag = true
                            currentOnDismissAll()
                            currentSetEditingHighlight(null)
                        }
                        if (didDrag) {
                            change.consume()
                            val activeLayout = latestLayoutResult() ?: layout
                            val focus = activeLayout.getOffsetForPosition(change.position).coerceIn(0, text.length)
                            if (focus != currentFocusOffset) {
                                currentFocusOffset = focus
                            }
                            val start = minOf(anchorOffset, currentFocusOffset)
                            val end = maxOf(anchorOffset, currentFocusOffset)
                            currentSetSelectionState(
                                TextSelectionState(
                                    isSelecting = start < end,
                                    isDragging = true,
                                    startOffset = start,
                                    endOffset = end,
                                    selectedText = if (start < end) text.text.substring(start, end) else "",
                                    isMouseSelection = true,
                                )
                            )
                        }
                    }
                } finally {
                    if (didDrag) {
                        val start = minOf(anchorOffset, currentFocusOffset)
                        val end = maxOf(anchorOffset, currentFocusOffset)
                        currentSetSelectionState(
                            if (completedNormally && latestSelectionState().isSelecting && start < end) {
                                TextSelectionState(
                                    isSelecting = true,
                                    isDragging = false,
                                    startOffset = start,
                                    endOffset = end,
                                    selectedText = text.text.substring(start, end),
                                    isMouseSelection = true,
                                )
                            } else {
                                TextSelectionState()
                            }
                        )
                    }
                }
                return@awaitEachGesture
            }

            // Touch / Stylus: detect tap on PointerEventPass.Main (so up.consume() runs
            // before parent dismissSelectionOnTap) or long-press after timeout.
            when (val gesture = awaitTouchTapOrLongPress(down)) {
                is TouchGestureStart.LongPress -> {
                    val layout = latestLayoutResult() ?: return@awaitEachGesture
                    val clampedInitialOffset = layout.getOffsetForPosition(gesture.position)
                        .coerceIn(0, text.text.lastIndex)
                    val wordRange = layout.getWordBoundary(clampedInitialOffset)
                    val anchorStart = wordRange.min.coerceIn(0, text.length)
                    val anchorEnd = wordRange.max.coerceIn(anchorStart, text.length)
                    if (anchorStart >= anchorEnd) return@awaitEachGesture

                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnDismissAll()
                    currentSetEditingHighlight(null)

                    var selStart = anchorStart
                    var selEnd = anchorEnd
                    var previousHandleOffset: Int? = null
                    currentSetSelectionState(
                        TextSelectionState(
                            isSelecting = true,
                            isDragging = true,
                            startOffset = selStart,
                            endOffset = selEnd,
                            selectedText = text.text.substring(selStart, selEnd),
                            isMouseSelection = false,
                        )
                    )

                    var lastProcessedPosition = gesture.position
                    currentSetDragPosition(gesture.position)
                    var completedNormally = false

                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            val position = change.position
                            val movementSinceLast = (position - lastProcessedPosition).getDistance()
                            if (movementSinceLast >= currentHysteresisPx) {
                                lastProcessedPosition = position
                                currentSetDragPosition(position)
                                val activeLayout = latestLayoutResult() ?: layout
                                val movedOffset = activeLayout.getOffsetForPosition(position).coerceIn(0, text.length)
                                val movingBackward = movedOffset < clampedInitialOffset
                                val fixedOffset = if (movingBackward) anchorEnd else anchorStart
                                val snapped = snapOffsetToWordBoundary(
                                    text = text.text,
                                    movedOffset = movedOffset,
                                    fixedOffset = fixedOffset,
                                    layoutResult = activeLayout,
                                    previousOffset = previousHandleOffset
                                )
                                previousHandleOffset = snapped
                                val nextStart = minOf(snapped, fixedOffset)
                                val nextEnd = maxOf(snapped, fixedOffset)
                                if (nextStart < nextEnd && (nextStart != selStart || nextEnd != selEnd)) {
                                    selStart = nextStart
                                    selEnd = nextEnd
                                    currentSetSelectionState(
                                        TextSelectionState(
                                            isSelecting = true,
                                            isDragging = true,
                                            startOffset = selStart,
                                            endOffset = selEnd,
                                            selectedText = text.text.substring(selStart, selEnd),
                                            isMouseSelection = false,
                                        )
                                    )
                                }
                            }
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        completedNormally = true
                    } finally {
                        currentSetDragPosition(Offset.Zero)
                        currentSetSelectionState(
                            if (completedNormally && latestSelectionState().isSelecting && selStart < selEnd) {
                                TextSelectionState(
                                    isSelecting = true,
                                    isDragging = false,
                                    startOffset = selStart,
                                    endOffset = selEnd,
                                    selectedText = text.text.substring(selStart, selEnd),
                                    isMouseSelection = false,
                                )
                            } else {
                                TextSelectionState()
                            }
                        )
                    }
                }

                is TouchGestureStart.Tap -> {
                    val upChange = gesture.upChange
                    if (upChange.isConsumed) return@awaitEachGesture
                    val layout = latestLayoutResult() ?: return@awaitEachGesture
                    val handled = handleTapOrMouseDoubleClick(
                        tapPosition = upChange.position,
                        uptimeMillis = upChange.uptimeMillis,
                        isMouse = false,
                        lastMouseClickTimeMs = -1L,
                        lastMouseClickPos = Offset.Zero,
                        doubleTapTimeoutMs = 0L,
                        doubleTapSlopPx = 0f,
                        updateLastClick = { _, _ -> },
                        text = text,
                        layout = layout,
                        highlights = currentHighlights,
                        setSelectionState = currentSetSelectionState,
                        setEditingHighlight = currentSetEditingHighlight,
                        onDismissAll = currentOnDismissAll,
                        onLinkClick = currentOnLinkClick,
                        onTooltipClick = currentOnTooltipClick,
                    )
                    if (handled) {
                        upChange.consume()
                    }
                }

                TouchGestureStart.Cancelled -> Unit
            }
        }
    }
}

private fun handleTapOrMouseDoubleClick(
    tapPosition: Offset,
    uptimeMillis: Long,
    isMouse: Boolean,
    lastMouseClickTimeMs: Long,
    lastMouseClickPos: Offset,
    doubleTapTimeoutMs: Long,
    doubleTapSlopPx: Float,
    updateLastClick: (Long, Offset) -> Unit,
    text: AnnotatedString,
    layout: TextLayoutResult,
    highlights: List<TextHighlight>,
    setSelectionState: (TextSelectionState) -> Unit,
    setEditingHighlight: (HighlightTapResult?) -> Unit,
    onDismissAll: () -> Unit,
    onLinkClick: ((String) -> Unit)?,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?,
): Boolean {
    val clearSelectionAndEditing = {
        setSelectionState(TextSelectionState())
        setEditingHighlight(null)
        onDismissAll()
    }

    if (text.isEmpty() || !isPositionWithinTextBounds(layout, tapPosition)) {
        clearSelectionAndEditing()
        return false
    }

    val offset = layout.getOffsetForPosition(tapPosition).coerceIn(0, text.text.lastIndex)

    if (isMouse) {
        val isDoubleClick = lastMouseClickTimeMs >= 0L &&
            (uptimeMillis - lastMouseClickTimeMs) <= doubleTapTimeoutMs &&
            (tapPosition - lastMouseClickPos).getDistance() <= doubleTapSlopPx
        if (isDoubleClick) {
            updateLastClick(-1L, Offset.Zero)
            val wordRange = layout.getWordBoundary(offset)
            val start = wordRange.min.coerceIn(0, text.length)
            val end = wordRange.max.coerceIn(start, text.length)
            if (start < end) {
                onDismissAll()
                setEditingHighlight(null)
                setSelectionState(
                    TextSelectionState(
                        isSelecting = true,
                        isDragging = false,
                        startOffset = start,
                        endOffset = end,
                        selectedText = text.text.substring(start, end),
                        isMouseSelection = true,
                    )
                )
                return true
            }
        } else {
            updateLastClick(uptimeMillis, tapPosition)
        }
    }

    // Check exact highlight hit first (so highlights covering links/tooltips remain editable)
    val exactHighlight = findTappedHighlight(
        highlights = highlights,
        tappedOffset = offset,
        textLength = text.length,
        allowAdjacentFallback = false,
    )
    if (exactHighlight != null) {
        onDismissAll()
        setSelectionState(TextSelectionState())
        setEditingHighlight(
            HighlightTapResult(
                highlight = exactHighlight,
                overlappingTooltip = extractTooltipAtOffset(text, offset),
                overlappingUrl = extractUrlAtOffset(text, offset),
            )
        )
        return true
    }

    // Check links/tooltips before ±1 adjacent-highlight fuzz so a highlight ending at offset N
    // never hijacks a link/tooltip starting at offset N.
    val handledAnnotatedTap = handleAnnotatedTextTap(
        text = text,
        offset = offset,
        onLinkClick = onLinkClick,
        onTooltipClick = onTooltipClick
    )
    if (handledAnnotatedTap) {
        clearSelectionAndEditing()
        return true
    }

    val nearbyHighlight = findTappedHighlight(
        highlights = highlights,
        tappedOffset = offset,
        textLength = text.length,
        allowAdjacentFallback = true,
    )
    if (nearbyHighlight != null) {
        onDismissAll()
        setSelectionState(TextSelectionState())
        setEditingHighlight(HighlightTapResult(highlight = nearbyHighlight))
        return true
    }

    clearSelectionAndEditing()
    return false
}

internal fun findTappedHighlight(
    highlights: List<TextHighlight>,
    tappedOffset: Int,
    textLength: Int,
    allowAdjacentFallback: Boolean = true,
): TextHighlight? {
    if (textLength <= 0 || highlights.isEmpty()) return null

    fun TextHighlight.containsOffset(offset: Int): Boolean {
        val start = startOffset.coerceAtLeast(0)
        val endExclusive = endOffset.coerceAtLeast(start)
        return offset in start until endExclusive
    }

    val smallestComparator = compareBy<TextHighlight> { it.endOffset - it.startOffset }
        .thenByDescending { it.id }

    val clampedTap = tappedOffset.coerceIn(0, textLength - 1)
    val exactCandidates = highlights.filter { it.containsOffset(clampedTap) }
    exactCandidates.minWithOrNull(smallestComparator)?.let { return it }

    if (!allowAdjacentFallback) return null

    val fallbackOffsets = listOf(clampedTap - 1, clampedTap + 1)
        .map { it.coerceIn(0, textLength - 1) }
        .distinct()

    val candidateHighlights = highlights.filter { highlight ->
        fallbackOffsets.any { offset -> highlight.containsOffset(offset) }
    }

    return candidateHighlights.minWithOrNull(smallestComparator)
}

internal fun snapOffsetToWordBoundary(
    text: String,
    movedOffset: Int,
    fixedOffset: Int,
    layoutResult: TextLayoutResult?,
    previousOffset: Int? = null
): Int {
    if (text.isEmpty()) return 0
    if (layoutResult == null) return movedOffset.coerceIn(0, text.length)

    val safeMoved = movedOffset.coerceIn(0, text.length)
    val clampedForWord = safeMoved.coerceIn(0, text.lastIndex)

    val wordRange = layoutResult.getWordBoundary(clampedForWord)
    val wordStart = wordRange.min
    val wordEnd = wordRange.max

    val movingBackward = safeMoved <= fixedOffset
    val primary = if (movingBackward) wordStart else wordEnd
    val secondary = if (movingBackward) wordEnd else wordStart

    val movedLine = layoutResult.getLineForOffset(clampedForWord)

    fun score(offset: Int): Int {
        val clampedOffset = offset.coerceIn(0, text.lastIndex)
        val lineDistance = kotlin.math.abs(layoutResult.getLineForOffset(clampedOffset) - movedLine)
        val offsetDistance = kotlin.math.abs(offset - safeMoved)
        return lineDistance * 1000 + offsetDistance
    }

    val primaryScore = score(primary)
    val secondaryScore = score(secondary)

    val chosen = when {
        primaryScore < secondaryScore -> primary
        secondaryScore < primaryScore -> secondary
        previousOffset != null -> {
            if (kotlin.math.abs(primary - previousOffset) <= kotlin.math.abs(secondary - previousOffset)) {
                primary
            } else {
                secondary
            }
        }
        else -> primary
    }
    return chosen.coerceIn(0, text.length)
}

private sealed interface TouchGestureStart {
    data class Tap(val upChange: PointerInputChange) : TouchGestureStart
    data class LongPress(val position: Offset) : TouchGestureStart
    data object Cancelled : TouchGestureStart
}

private suspend fun AwaitPointerEventScope.awaitTouchTapOrLongPress(
    down: PointerInputChange,
): TouchGestureStart {
    val downPos = down.position
    val touchSlop = viewConfiguration.touchSlop
    var lastPosition = downPos

    val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var gesture: TouchGestureStart? = null
        while (gesture == null) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || event.changes.any { it.id != down.id && it.pressed } || change.isConsumed) {
                gesture = TouchGestureStart.Cancelled
            } else if (!change.pressed) {
                gesture = if (
                    change.changedToUp() &&
                    !change.isOutOfBounds(size, extendedTouchPadding) &&
                    (change.position - downPos).getDistance() <= touchSlop
                ) {
                    TouchGestureStart.Tap(change)
                } else {
                    TouchGestureStart.Cancelled
                }
            } else {
                lastPosition = change.position
                if (
                    change.isOutOfBounds(size, extendedTouchPadding) ||
                    (change.position - downPos).getDistance() > touchSlop
                ) {
                    gesture = TouchGestureStart.Cancelled
                } else {
                    val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                    val finalChange = finalEvent.changes.firstOrNull { it.id == down.id }
                    if (finalChange == null || finalChange.isConsumed) {
                        gesture = TouchGestureStart.Cancelled
                    }
                }
            }
        }
        gesture
    }

    return result ?: TouchGestureStart.LongPress(lastPosition)
}
