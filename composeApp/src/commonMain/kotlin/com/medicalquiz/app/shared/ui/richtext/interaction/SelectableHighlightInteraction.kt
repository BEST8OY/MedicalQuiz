package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.models.TextHighlight

private val SELECTION_HANDLE_VISUAL_RADIUS = 7.dp
private val SELECTION_HANDLE_TOUCH_TARGET = 36.dp

/**
 * State for text selection within SelectableRichText.
 */
internal data class TextSelectionState(
    val isSelecting: Boolean = false,
    val isDragging: Boolean = false,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val selectedText: String = "",
    val anchorPosition: Offset = Offset.Zero
) {
    val hasSelectionRange: Boolean get() = isSelecting && startOffset != endOffset
    val showSelectionToolbar: Boolean get() = hasSelectionRange && !isDragging
    val selectionRange: IntRange get() = minOf(startOffset, endOffset) until maxOf(startOffset, endOffset)
}

internal fun Modifier.selectableHighlightGestures(
    text: AnnotatedString,
    highlightedText: AnnotatedString,
    highlights: List<TextHighlight>,
    highlightsById: Map<Long, TextHighlight>,
    longPressDragHysteresisPx: Float,
    currentLayoutResult: () -> TextLayoutResult?,
    currentSelectionState: () -> TextSelectionState,
    setSelectionState: (TextSelectionState) -> Unit,
    setEditingHighlight: (TextHighlight?) -> Unit,
    setEditPopupAnchor: (Offset) -> Unit,
    onLinkClick: ((String) -> Unit)?,
    onTooltipClick: ((String) -> Unit)?
): Modifier {
    return pointerInput(text, highlights) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)

            if (longPress != null) {
                currentLayoutResult()?.let { layout ->
                    val offset = layout.getOffsetForPosition(longPress.position)
                    val (start, end) = expandToWordBoundaries(text.text, offset)
                    setSelectionState(
                        TextSelectionState(
                            isSelecting = true,
                            isDragging = true,
                            startOffset = start,
                            endOffset = end,
                            selectedText = text.text.substring(start, end),
                            anchorPosition = calculateRangeAnchor(
                                layoutResult = layout,
                                textLength = text.length,
                                startOffset = start,
                                endOffset = end,
                                fallbackAnchor = longPress.position
                            )
                        )
                    )
                }

                var lastProcessedPosition = longPress.position
                do {
                    val event = awaitPointerEvent()
                    val position = event.changes.firstOrNull()?.position ?: break
                    val movementSinceLast = (position - lastProcessedPosition).getDistance()
                    if (movementSinceLast < longPressDragHysteresisPx) {
                        event.changes.forEach { it.consume() }
                        continue
                    }
                    lastProcessedPosition = position

                    currentLayoutResult()?.let { layout ->
                        val selectionState = currentSelectionState()
                        val offset = layout.getOffsetForPosition(position)
                        val newEnd = snapOffsetToWordBoundary(
                            text = text.text,
                            movedOffset = offset.coerceIn(0, text.length),
                            fixedOffset = selectionState.startOffset,
                            layoutResult = layout,
                            previousOffset = selectionState.endOffset
                        )
                        if (newEnd == selectionState.endOffset) {
                            event.changes.forEach { it.consume() }
                            return@let
                        }
                        setSelectionState(
                            updateSelectionFromHandleOffset(
                                currentState = selectionState,
                                movingStartHandle = false,
                                newHandleOffset = newEnd,
                                textContent = text.text,
                                layoutResult = layout,
                                textLength = text.length,
                                fallbackAnchor = position
                            )
                        )
                    }

                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })

                setSelectionState(finishSelectionDrag(currentSelectionState()))
            } else {
                var upPosition = down.position
                var movedTooFar = false
                val tapSlopPx = 12f

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    upPosition = change.position
                    if ((upPosition - down.position).getDistance() > tapSlopPx) {
                        movedTooFar = true
                    }
                    if (!change.pressed) break
                }

                if (movedTooFar) {
                    return@awaitEachGesture
                }

                currentLayoutResult()?.let { layout ->
                    val offset = layout.getOffsetForPosition(upPosition)

                    val tappedHighlight = findTappedHighlight(
                        annotatedText = highlightedText,
                        highlightsById = highlightsById,
                        tappedOffset = offset,
                        textLength = text.length
                    )
                    if (tappedHighlight != null) {
                        setEditingHighlight(tappedHighlight)
                        setEditPopupAnchor(
                            calculateRangeAnchor(
                                layoutResult = layout,
                                textLength = text.length,
                                startOffset = tappedHighlight.startOffset,
                                endOffset = tappedHighlight.endOffset,
                                fallbackAnchor = upPosition
                            )
                        )
                        setSelectionState(TextSelectionState())
                        return@awaitEachGesture
                    }

                    text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                        onLinkClick?.invoke(it.item)
                        return@awaitEachGesture
                    }
                    text.getStringAnnotations("TOOLTIP", offset, offset).firstOrNull()?.let {
                        onTooltipClick?.invoke(it.item)
                        return@awaitEachGesture
                    }

                    setSelectionState(TextSelectionState())
                    setEditingHighlight(null)
                }
            }
        }
    }
}

@Composable
internal fun SelectionHandle(
    x: Float,
    y: Float,
    containerSize: IntSize,
    dragHysteresisPx: Float,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Offset) -> Unit
) {
    val handleRadius = SELECTION_HANDLE_VISUAL_RADIUS
    val handleTouchTarget = SELECTION_HANDLE_TOUCH_TARGET
    val handleColor = MaterialTheme.colorScheme.primary
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleDiameterPx = with(density) {
        (handleRadius * 2).toPx()
    }
    val touchTargetPx = with(density) {
        handleTouchTarget.toPx()
    }

    val maxVisualX = (containerSize.width - handleDiameterPx).coerceAtLeast(0f)
    val maxVisualY = (containerSize.height - handleDiameterPx).coerceAtLeast(0f)
    val visualX = (x - handleDiameterPx / 2f).coerceIn(0f, maxVisualX)
    val visualY = y.coerceIn(0f, maxVisualY)
    val visualCenter = Offset(
        x = visualX + handleDiameterPx / 2f,
        y = visualY + handleDiameterPx / 2f
    )

    val maxTouchX = (containerSize.width - touchTargetPx).coerceAtLeast(0f)
    val maxTouchY = (containerSize.height - touchTargetPx).coerceAtLeast(0f)
    val adjustedX = (visualCenter.x - touchTargetPx / 2f).coerceIn(0f, maxTouchX)
    val adjustedY = (visualCenter.y - touchTargetPx / 2f).coerceIn(0f, maxTouchY)

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = adjustedX
                translationY = adjustedY
            }
            .size(handleTouchTarget)
            .drawBehind {
                drawCircle(
                    color = handleColor,
                    radius = handleDiameterPx / 2f,
                    center = Offset(
                        x = visualCenter.x - adjustedX,
                        y = visualCenter.y - adjustedY
                    )
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onDragStart()

                    var currentAbsolutePosition = visualCenter
                    var lastDispatchedPosition: Offset? = null
                    var pointerPressed = true
                    while (pointerPressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        currentAbsolutePosition = Offset(
                            x = (currentAbsolutePosition.x + change.positionChange().x)
                                .coerceIn(0f, containerSize.width.toFloat()),
                            y = (currentAbsolutePosition.y + change.positionChange().y)
                                .coerceIn(0f, containerSize.height.toFloat())
                        )
                        val shouldDispatch = lastDispatchedPosition == null ||
                            (currentAbsolutePosition - lastDispatchedPosition!!).getDistance() >= dragHysteresisPx

                        if (shouldDispatch) {
                            onDrag(currentAbsolutePosition)
                            lastDispatchedPosition = currentAbsolutePosition
                        }
                        change.consume()
                        pointerPressed = change.pressed
                    }
                    onDragEnd()
                }
            }
    )
}

internal fun findTappedHighlight(
    annotatedText: AnnotatedString,
    highlightsById: Map<Long, TextHighlight>,
    tappedOffset: Int,
    textLength: Int
): TextHighlight? {
    if (textLength <= 0) return null

    val candidateOffsets = listOf(tappedOffset, tappedOffset - 1, tappedOffset + 1)
        .map { it.coerceIn(0, textLength - 1) }
        .distinct()

    val candidateHighlights = candidateOffsets
        .mapNotNull { offset ->
            annotatedText
                .getStringAnnotations("HIGHLIGHT", offset, offset)
                .firstOrNull()
                ?.item
                ?.toLongOrNull()
                ?.let(highlightsById::get)
        }

    return candidateHighlights
        .maxByOrNull { it.endOffset - it.startOffset }
}

internal fun expandToWordBoundaries(text: String, offset: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0

    val safeOffset = offset.coerceIn(0, text.lastIndex)

    val pivot = findNearestWordPivot(text, safeOffset)
        ?: return safeOffset to (safeOffset + 1).coerceAtMost(text.length)

    var start = pivot
    while (start > 0 && text.isWordSelectionCharAt(start - 1)) {
        start--
    }

    var end = pivot + 1
    while (end < text.length && text.isWordSelectionCharAt(end)) {
        end++
    }

    while (start < end && !text[start].isLetterOrDigit()) {
        start++
    }
    while (end > start && !text[end - 1].isLetterOrDigit()) {
        end--
    }

    if (start >= end) {
        return pivot to (pivot + 1).coerceAtMost(text.length)
    }

    return start to end
}

internal fun snapOffsetToWordBoundary(
    text: String,
    movedOffset: Int,
    fixedOffset: Int,
    layoutResult: TextLayoutResult?,
    previousOffset: Int? = null
): Int {
    if (text.isEmpty()) return 0

    val safeMoved = movedOffset.coerceIn(0, text.length)
    val clampedForWord = safeMoved.coerceIn(0, text.lastIndex)
    val (wordStart, wordEnd) = expandToWordBoundaries(text, clampedForWord)

    val movingBackward = safeMoved <= fixedOffset
    val primary = if (movingBackward) wordStart else wordEnd
    val secondary = if (movingBackward) wordEnd else wordStart

    if (layoutResult == null) {
        return primary.coerceIn(0, text.length)
    }

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

private fun findNearestWordPivot(text: String, offset: Int): Int? {
    if (text.isWordSelectionCharAt(offset)) return offset

    var right = offset + 1
    while (right < text.length) {
        if (text.isWordSelectionCharAt(right)) break
        right++
    }

    var left = offset - 1
    while (left >= 0) {
        if (text.isWordSelectionCharAt(left)) break
        left--
    }

    return when {
        right < text.length && left >= 0 -> {
            if ((right - offset) <= (offset - left)) right else left
        }
        right < text.length -> right
        left >= 0 -> left
        else -> null
    }
}

private fun String.isWordSelectionCharAt(index: Int): Boolean {
    if (index !in indices) return false
    val character = this[index]
    if (character.isLetterOrDigit()) return true

    if (character == '\'' || character == '’' || character == '-') {
        val previous = index - 1
        val next = index + 1
        return previous in indices && next in indices &&
            this[previous].isLetterOrDigit() && this[next].isLetterOrDigit()
    }

    return false
}

internal fun finishSelectionDrag(state: TextSelectionState): TextSelectionState {
    return if (state.startOffset != state.endOffset) {
        state.copy(isDragging = false)
    } else {
        TextSelectionState()
    }
}

internal fun updateSelectionFromHandleOffset(
    currentState: TextSelectionState,
    movingStartHandle: Boolean,
    newHandleOffset: Int,
    textContent: String,
    layoutResult: TextLayoutResult,
    textLength: Int,
    fallbackAnchor: Offset
): TextSelectionState {
    val previousOffset = if (movingStartHandle) {
        currentState.startOffset
    } else {
        currentState.endOffset
    }
    if (newHandleOffset == previousOffset) return currentState

    val fixedOffset = if (movingStartHandle) {
        currentState.endOffset
    } else {
        currentState.startOffset
    }
    val actualStart = minOf(newHandleOffset, fixedOffset)
    val actualEnd = maxOf(newHandleOffset, fixedOffset)
    if (actualEnd <= actualStart) return currentState

    return if (movingStartHandle) {
        currentState.copy(
            startOffset = newHandleOffset,
            selectedText = textContent.substring(actualStart, actualEnd),
            anchorPosition = calculateRangeAnchor(
                layoutResult = layoutResult,
                textLength = textLength,
                startOffset = actualStart,
                endOffset = actualEnd,
                fallbackAnchor = fallbackAnchor
            )
        )
    } else {
        currentState.copy(
            endOffset = newHandleOffset,
            selectedText = textContent.substring(actualStart, actualEnd),
            anchorPosition = calculateRangeAnchor(
                layoutResult = layoutResult,
                textLength = textLength,
                startOffset = actualStart,
                endOffset = actualEnd,
                fallbackAnchor = fallbackAnchor
            )
        )
    }
}
