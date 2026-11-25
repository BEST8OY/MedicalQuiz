package com.medqb.app.shared.ui.richtext.positioning

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

@Immutable
internal class SelectionHandlePositionProvider(
    private val anchorInText: Offset,
    private val isStartHandle: Boolean,
    private val touchSizePx: Int,
    private val visualSizePx: Int,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val xOffset = if (isStartHandle) {
            (touchSizePx + visualSizePx) / 2
        } else {
            (touchSizePx - visualSizePx) / 2
        }
        return IntOffset(
            x = anchorBounds.left + anchorInText.x.roundToInt() - xOffset,
            y = anchorBounds.top + anchorInText.y.roundToInt(),
        )
    }
}

internal fun calculateSelectionRectInAnchor(
    layoutResult: TextLayoutResult,
    textLength: Int,
    startOffset: Int,
    endOffset: Int,
): Rect {
    if (textLength <= 0) return Rect.Zero

    val start = minOf(startOffset, endOffset).coerceIn(0, textLength - 1)
    val endExclusive = maxOf(startOffset, endOffset).coerceIn(start + 1, textLength)
    val end = (endExclusive - 1).coerceIn(start, textLength - 1)

    val startLine = layoutResult.getLineForOffset(start)
    val endLine = layoutResult.getLineForOffset(end)
    val minLine = minOf(startLine, endLine)
    val maxLine = maxOf(startLine, endLine)

    val startBox = layoutResult.getBoundingBox(start)
    val endBox = layoutResult.getBoundingBox(end)

    val left = if (startLine == endLine) {
        minOf(startBox.left, endBox.left)
    } else {
        layoutResult.getLineLeft(minLine)
    }
    val right = if (startLine == endLine) {
        maxOf(startBox.right, endBox.right)
    } else {
        layoutResult.getLineRight(minLine)
    }

    return Rect(
        left = left,
        top = layoutResult.getLineTop(minLine),
        right = right,
        bottom = layoutResult.getLineBottom(maxLine),
    )
}

@Immutable
internal class SelectionToolbarPositionProvider(
    private val selectionRectInAnchor: Rect,
    private val edgeMarginPx: Int,
    private val topSafeMarginPx: Int,
    private val bottomSafeMarginPx: Int,
    private val aboveGapPx: Int,
    private val belowGapPx: Int,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val selTop = anchorBounds.top + selectionRectInAnchor.top.roundToInt()
        val selBottom = anchorBounds.top + selectionRectInAnchor.bottom.roundToInt()
        val selCenterX = anchorBounds.left + ((selectionRectInAnchor.left + selectionRectInAnchor.right) / 2f).roundToInt()

        val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val x = (selCenterX - popupContentSize.width / 2).coerceIn(edgeMarginPx, maxX)

        val aboveY = selTop - aboveGapPx - popupContentSize.height
        val belowY = selBottom + belowGapPx
        val maxY = (windowSize.height - popupContentSize.height - bottomSafeMarginPx).coerceAtLeast(topSafeMarginPx)

        val roomAbove = selTop - aboveGapPx - topSafeMarginPx
        val roomBelow = windowSize.height - bottomSafeMarginPx - (selBottom + belowGapPx)

        val y = when {
            aboveY >= topSafeMarginPx -> aboveY
            belowY <= maxY -> belowY
            roomAbove >= roomBelow -> aboveY.coerceIn(topSafeMarginPx, maxY)
            else -> belowY.coerceIn(topSafeMarginPx, maxY)
        }

        return IntOffset(x = x, y = y)
    }
}
