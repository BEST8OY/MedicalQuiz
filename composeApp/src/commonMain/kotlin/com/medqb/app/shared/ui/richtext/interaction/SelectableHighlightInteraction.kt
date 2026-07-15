package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import com.medqb.app.shared.data.models.TextHighlight

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

internal data class InitialSelectionAnchor(
    val pressOffset: Int,
    val startOffset: Int,
    val endOffset: Int
)

internal data class SelectionDragUpdate(
    val movingStartHandle: Boolean,
    val fixedOffset: Int,
    val previousHandleOffset: Int
)

internal data class SelectionOffsetUpdate(
    val startOffset: Int,
    val endOffset: Int,
    val actualStart: Int,
    val actualEnd: Int
)

internal fun Modifier.selectableHighlightGestures(
    text: AnnotatedString,
    highlights: List<TextHighlight>,
    longPressDragHysteresisPx: Float,
    currentLayoutResult: () -> TextLayoutResult?,
    currentSelectionState: () -> TextSelectionState,
    setSelectionState: (TextSelectionState) -> Unit,
    setEditingHighlight: (TextHighlight?) -> Unit,
    setEditPopupAnchor: (Offset) -> Unit,
    setDragPosition: (Offset) -> Unit,
    onLinkClick: ((String) -> Unit)?,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)?
): Modifier {
    return pointerInput(text, highlights) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)

            if (longPress != null) {
                var initialSelectionAnchor: InitialSelectionAnchor? = null

                currentLayoutResult()?.let { layout ->
                    val offset = layout.getOffsetForPosition(longPress.position)

                    // Use TextLayoutResult.getWordBoundary() for accurate, layout-aware word detection
                    val wordRange = layout.getWordBoundary(offset)
                    val start = wordRange.min
                    val end = wordRange.max
                    initialSelectionAnchor = InitialSelectionAnchor(
                        pressOffset = offset,
                        startOffset = start,
                        endOffset = end
                    )
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
                setDragPosition(longPress.position)
                do {
                    val event = awaitPointerEvent()
                    val position = event.changes.firstOrNull()?.position ?: break
                    val movementSinceLast = (position - lastProcessedPosition).getDistance()
                    if (movementSinceLast < longPressDragHysteresisPx) {
                        event.changes.forEach { it.consume() }
                        continue
                    }
                    lastProcessedPosition = position
                    setDragPosition(position)

                    currentLayoutResult()?.let { layout ->
                        val selectionState = currentSelectionState()
                        val offset = layout.getOffsetForPosition(position)
                        val dragUpdate = resolveSelectionDragUpdate(
                            currentState = selectionState,
                            movedOffset = offset,
                            initialSelectionAnchor = initialSelectionAnchor
                        )
                        val movingStartHandle = dragUpdate.movingStartHandle
                        val fixedOffset = dragUpdate.fixedOffset
                        val newHandleOffset = snapOffsetToWordBoundary(
                            text = text.text,
                            movedOffset = offset.coerceIn(0, text.length),
                            fixedOffset = fixedOffset,
                            layoutResult = layout,
                            previousOffset = dragUpdate.previousHandleOffset
                        )
                        val previousHandleOffset = dragUpdate.previousHandleOffset
                        val previousFixedOffset = if (movingStartHandle) {
                            selectionState.endOffset
                        } else {
                            selectionState.startOffset
                        }
                        if (newHandleOffset == previousHandleOffset && fixedOffset == previousFixedOffset) {
                            event.changes.forEach { it.consume() }
                            return@let
                        }
                        setSelectionState(
                            updateSelectionFromHandleOffset(
                                currentState = selectionState,
                                movingStartHandle = movingStartHandle,
                                newHandleOffset = newHandleOffset,
                                fixedOffset = fixedOffset,
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
                setDragPosition(Offset.Zero)
            } else {
                val upChange = currentEvent.changes.firstOrNull { !it.pressed } ?: return@awaitEachGesture
                if (upChange.isConsumed) {
                    return@awaitEachGesture
                }

                val tapPosition = upChange.position
                val clearSelectionAndEditing = {
                    setSelectionState(TextSelectionState())
                    setEditingHighlight(null)
                }

                currentLayoutResult()?.let { layout ->
                    val offset = layout.getOffsetForPosition(tapPosition)

                    val handledAnnotatedTap = handleAnnotatedTextTap(
                        text = text,
                        offset = offset,
                        onLinkClick = onLinkClick,
                        onTooltipClick = onTooltipClick
                    )
                    if (handledAnnotatedTap) {
                        clearSelectionAndEditing()
                        return@awaitEachGesture
                    }

                    val tappedHighlight = findTappedHighlight(
                        highlights = highlights,
                        tappedOffset = offset,
                        textLength = text.length
                    )
                    if (tappedHighlight == null) {
                        clearSelectionAndEditing()
                        return@awaitEachGesture
                    }

                    setEditingHighlight(tappedHighlight)
                    setEditPopupAnchor(
                        calculateRangeAnchor(
                            layoutResult = layout,
                            textLength = text.length,
                            startOffset = tappedHighlight.startOffset,
                            endOffset = tappedHighlight.endOffset,
                            fallbackAnchor = tapPosition
                        )
                    )
                }
            }
        }
    }
}

internal fun findTappedHighlight(
    highlights: List<TextHighlight>,
    tappedOffset: Int,
    textLength: Int
): TextHighlight? {
    if (textLength <= 0) return null

    fun TextHighlight.containsOffset(offset: Int): Boolean {
        val start = startOffset.coerceAtLeast(0)
        val endExclusive = endOffset.coerceAtLeast(start)
        return offset in start until endExclusive
    }

    val clampedTap = tappedOffset.coerceIn(0, textLength - 1)
    val exactCandidates = highlights.filter { it.containsOffset(clampedTap) }
    exactCandidates.maxByOrNull(TextHighlight::length)?.let { return it }

    val fallbackOffsets = listOf(clampedTap - 1, clampedTap + 1)
        .map { it.coerceIn(0, textLength - 1) }
        .distinct()

    val candidateHighlights = highlights.filter { highlight ->
        fallbackOffsets.any { offset -> highlight.containsOffset(offset) }
    }

    return candidateHighlights.maxByOrNull(TextHighlight::length)
}

private fun TextHighlight.length(): Int = endOffset - startOffset

internal fun expandToWordBoundaries(text: String, offset: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0

    val safeOffset = offset.coerceIn(0, text.lastIndex)

    expandToSpecialCharacterCluster(text, safeOffset)?.let { return it }

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

    while (start < end && text[start].isLeadingTrimChar()) {
        start++
    }
    while (end > start && text[end - 1].isTrailingTrimChar()) {
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

    // Use TextLayoutResult.getWordBoundary() for accurate, layout-aware word detection
    val wordRange = if (layoutResult != null) {
        layoutResult.getWordBoundary(clampedForWord)
    } else {
        // Fallback to character-class heuristics when layout is unavailable
        val (ws, we) = expandToWordBoundaries(text, clampedForWord)
        androidx.compose.ui.text.TextRange(ws, we)
    }
    val wordStart = wordRange.min
    val wordEnd = wordRange.max

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

private fun expandToSpecialCharacterCluster(text: String, offset: Int): Pair<Int, Int>? {
    // Selecting punctuation directly should keep intentional marks, such as ellipses,
    // instead of jumping to a nearby word that the user did not press.
    if (!text.isSelectableSpecialCharacterAt(offset)) return null

    var start = offset
    while (start > 0 && text.isMatchingSpecialClusterChar(start - 1, offset)) {
        start--
    }

    var end = offset + 1
    while (end < text.length && text.isMatchingSpecialClusterChar(end, offset)) {
        end++
    }

    return start to end
}

private fun String.isMatchingSpecialClusterChar(index: Int, pivotIndex: Int): Boolean {
    if (index !in indices || pivotIndex !in indices) return false

    val pivot = this[pivotIndex]
    val candidate = this[index]
    return when {
        pivot == '.' || pivot == '…' -> candidate == '.' || candidate == '…'
        pivot.isDashCharacter() -> candidate.isDashCharacter()
        pivot.isQuoteCharacter() -> candidate.isQuoteCharacter()
        pivot.isBracketCharacter() -> candidate == pivot
        else -> candidate == pivot
    }
}

private fun String.isSelectableSpecialCharacterAt(index: Int): Boolean {
    if (index !in indices) return false
    val character = this[index]

    if (character.isLetterOrDigit() || character.isWhitespace()) return false
    if (isWordSelectionCharAt(index)) return false

    if (character == '.') {
        val previousIsDigit = (index - 1) in indices && this[index - 1].isDigit()
        val nextIsDigit = (index + 1) in indices && this[index + 1].isDigit()
        if (previousIsDigit && nextIsDigit) return false
    }

    return character.isEllipsisCharacter() ||
        character.isDashCharacter() ||
        character.isQuoteCharacter() ||
        character.isBracketCharacter() ||
        character.isGeneralPunctuation()
}

private fun findNearestWordPivot(text: String, offset: Int): Int? {
    if (text.isWordSelectionCharAt(offset)) return offset

    val left = offset - 1
    if (left >= 0 && text.isWordSelectionCharAt(left)) return left

    val right = offset + 1
    if (right < text.length && text.isWordSelectionCharAt(right)) return right

    return null
}

private fun String.isWordSelectionCharAt(index: Int): Boolean {
    if (index !in indices) return false
    val character = this[index]
    if (character.isLetterOrDigit()) return true
    if (character.isCombiningMark()) return true

    if (character.isInfixWordConnector()) {
        val previous = index - 1
        val next = index + 1
        return previous in indices && next in indices &&
            this[previous].isWordCoreChar() && this[next].isWordCoreChar()
    }

    if (character == ',' || character == ':') {
        val previous = index - 1
        val next = index + 1
        return previous in indices && next in indices &&
            this[previous].isDigit() && this[next].isDigit()
    }

    if (character == '+' || character == '#') {
        return hasWordCoreBefore(index)
    }

    return false
}

private fun String.hasWordCoreBefore(index: Int): Boolean {
    var cursor = index - 1
    while (cursor in indices && this[cursor] == '+') {
        cursor--
    }
    return cursor in indices && this[cursor].isLetter()
}

private fun Char.isWordCoreChar(): Boolean = isLetterOrDigit() || isCombiningMark()

private fun Char.isCombiningMark(): Boolean {
    return category == CharCategory.NON_SPACING_MARK ||
        category == CharCategory.COMBINING_SPACING_MARK ||
        category == CharCategory.ENCLOSING_MARK
}

private fun Char.isInfixWordConnector(): Boolean {
    return this == '\'' ||
        this == '’' ||
        this == '-' ||
        this == '‐' ||
        this == '‑' ||
        this == '–' ||
        this == '/' ||
        this == '.'
}

private fun Char.isLeadingTrimChar(): Boolean = isQuoteCharacter() || isOpeningBracket()

private fun Char.isTrailingTrimChar(): Boolean {
    return isQuoteCharacter() || isClosingBracket() || isSentencePunctuation()
}

private fun Char.isSentencePunctuation(): Boolean {
    return this == '.' || this == ',' || this == ';' || this == ':' || this == '!' || this == '?'
}

private fun Char.isEllipsisCharacter(): Boolean = this == '.' || this == '…'

private fun Char.isDashCharacter(): Boolean {
    return this == '-' || this == '‐' || this == '‑' || this == '–' || this == '—'
}

private fun Char.isQuoteCharacter(): Boolean {
    return this == '\'' || this == '"' || this == '‘' || this == '’' || this == '“' || this == '”'
}

private fun Char.isBracketCharacter(): Boolean = isOpeningBracket() || isClosingBracket()

private fun Char.isOpeningBracket(): Boolean = this == '(' || this == '[' || this == '{' || this == '<'

private fun Char.isClosingBracket(): Boolean = this == ')' || this == ']' || this == '}' || this == '>'

private fun Char.isGeneralPunctuation(): Boolean {
    return category == CharCategory.DASH_PUNCTUATION ||
        category == CharCategory.START_PUNCTUATION ||
        category == CharCategory.END_PUNCTUATION ||
        category == CharCategory.CONNECTOR_PUNCTUATION ||
        category == CharCategory.OTHER_PUNCTUATION ||
        category == CharCategory.MATH_SYMBOL ||
        category == CharCategory.CURRENCY_SYMBOL ||
        category == CharCategory.MODIFIER_SYMBOL ||
        category == CharCategory.OTHER_SYMBOL
}

internal fun finishSelectionDrag(state: TextSelectionState): TextSelectionState {
    return if (state.startOffset != state.endOffset) {
        state.copy(isDragging = false)
    } else {
        TextSelectionState()
    }
}

internal fun resolveSelectionDragUpdate(
    currentState: TextSelectionState,
    movedOffset: Int,
    initialSelectionAnchor: InitialSelectionAnchor?
): SelectionDragUpdate {
    if (initialSelectionAnchor != null) {
        val movingStartHandle = movedOffset < initialSelectionAnchor.pressOffset
        return if (movingStartHandle) {
            SelectionDragUpdate(
                movingStartHandle = true,
                fixedOffset = initialSelectionAnchor.endOffset,
                previousHandleOffset = currentState.startOffset
            )
        } else {
            SelectionDragUpdate(
                movingStartHandle = false,
                fixedOffset = initialSelectionAnchor.startOffset,
                previousHandleOffset = currentState.endOffset
            )
        }
    }

    // Null-anchor fallback: only reachable if layout was null at long-press
    // but succeeds mid-drag. In practice this path is dead code — the anchor
    // is always set before entering the drag loop. Kept as defensive guard.
    val movingStartHandle = movedOffset < currentState.startOffset
    return SelectionDragUpdate(
        movingStartHandle = movingStartHandle,
        fixedOffset = if (movingStartHandle) currentState.endOffset else currentState.startOffset,
        previousHandleOffset = if (movingStartHandle) currentState.startOffset else currentState.endOffset
    )
}

internal fun calculateSelectionOffsetsFromHandleOffset(
    currentState: TextSelectionState,
    movingStartHandle: Boolean,
    newHandleOffset: Int,
    fixedOffset: Int? = null
): SelectionOffsetUpdate? {
    val resolvedFixedOffset = fixedOffset ?: if (movingStartHandle) {
        currentState.endOffset
    } else {
        currentState.startOffset
    }

    val actualStart = minOf(newHandleOffset, resolvedFixedOffset)
    val actualEnd = maxOf(newHandleOffset, resolvedFixedOffset)
    if (actualEnd <= actualStart) return null

    return SelectionOffsetUpdate(
        startOffset = if (movingStartHandle) newHandleOffset else resolvedFixedOffset,
        endOffset = if (movingStartHandle) resolvedFixedOffset else newHandleOffset,
        actualStart = actualStart,
        actualEnd = actualEnd
    )
}

internal fun updateSelectionFromHandleOffset(
    currentState: TextSelectionState,
    movingStartHandle: Boolean,
    newHandleOffset: Int,
    fixedOffset: Int? = null,
    textContent: String,
    layoutResult: TextLayoutResult,
    textLength: Int,
    fallbackAnchor: Offset
): TextSelectionState {
    val resolvedFixedOffset = fixedOffset ?: if (movingStartHandle) {
        currentState.endOffset
    } else {
        currentState.startOffset
    }
    val previousOffset = if (movingStartHandle) {
        currentState.startOffset
    } else {
        currentState.endOffset
    }
    val previousFixedOffset = if (movingStartHandle) {
        currentState.endOffset
    } else {
        currentState.startOffset
    }
    if (newHandleOffset == previousOffset && resolvedFixedOffset == previousFixedOffset) return currentState

    val offsetUpdate = calculateSelectionOffsetsFromHandleOffset(
        currentState = currentState,
        movingStartHandle = movingStartHandle,
        newHandleOffset = newHandleOffset,
        fixedOffset = resolvedFixedOffset
    ) ?: return currentState

    return currentState.copy(
        startOffset = offsetUpdate.startOffset,
        endOffset = offsetUpdate.endOffset,
        selectedText = textContent.substring(offsetUpdate.actualStart, offsetUpdate.actualEnd),
        anchorPosition = calculateRangeAnchor(
            layoutResult = layoutResult,
            textLength = textLength,
            startOffset = offsetUpdate.actualStart,
            endOffset = offsetUpdate.actualEnd,
            fallbackAnchor = fallbackAnchor
        )
    )
}
