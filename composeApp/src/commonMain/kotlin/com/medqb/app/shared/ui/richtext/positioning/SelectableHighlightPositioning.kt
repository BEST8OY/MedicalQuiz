package com.medqb.app.shared.ui.richtext

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize

/** Popup placement constants in px. Single-sourced to avoid drift across functions. */
private const val POPUP_PADDING_PX = 8f
private const val POPUP_GAP_PX = 12f

/** Shared guard: popup/container haven't been measured yet. */
private fun isNotMeasured(containerSize: IntSize, popupSize: IntSize): Boolean =
    containerSize == IntSize.Zero || popupSize == IntSize.Zero

/** Shared guard: does a popup of [requiredSize] fit within [availableSpace] given [padding]? */
private fun fitsInSpace(requiredSize: Float, availableSpace: Float, padding: Float): Boolean =
    requiredSize + padding * 2 <= availableSpace

internal fun calculateRangeAnchor(
    layoutResult: TextLayoutResult,
    textLength: Int,
    startOffset: Int,
    endOffset: Int,
    fallbackAnchor: Offset
): Offset {
    if (textLength <= 0) return fallbackAnchor

    val start = minOf(startOffset, endOffset).coerceIn(0, textLength - 1)
    val endExclusive = maxOf(startOffset, endOffset).coerceIn(start + 1, textLength)
    val end = (endExclusive - 1).coerceIn(start, textLength - 1)

    val startBox = layoutResult.getBoundingBox(start)
    val endBox = layoutResult.getBoundingBox(end)

    // Horizontal center based on first and last character boxes.
    // For multi-line selections with varying line widths this is approximate;
    // true multi-line bbox centering is unnecessary for toolbar placement.
    val minLeft = minOf(startBox.left, endBox.left)
    val maxRight = maxOf(startBox.right, endBox.right)
    val centerX = (minLeft + maxRight) / 2f
    // Anchor vertically at the top of the selection (first line).
    val anchorY = minOf(startBox.top, endBox.top)

    return Offset(
        x = centerX,
        y = anchorY
    )
}

internal fun calculatePopupPosition(
    anchorPosition: Offset,
    popupSize: IntSize,
    containerSize: IntSize,
    preferAbove: Boolean
): Offset {
    if (isNotMeasured(containerSize, popupSize)) return anchorPosition

    val padding = POPUP_PADDING_PX
    val gap = POPUP_GAP_PX
    val popupWidth = popupSize.width.toFloat()
    val popupHeight = popupSize.height.toFloat()
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()

    val minX = padding
    val maxX = (containerWidth - popupWidth - padding).coerceAtLeast(minX)
    val centeredX = anchorPosition.x - (popupWidth / 2f)
    val x = centeredX.coerceIn(minX, maxX)

    // Compute raw candidate positions, THEN check if they fit, THEN clamp.
    val rawAboveY = anchorPosition.y - popupHeight - gap
    val rawBelowY = anchorPosition.y + gap

    val fitsAbove = fitsInSpace(popupHeight, rawAboveY + popupHeight, padding)
        || rawAboveY >= padding
    val fitsBelow = rawBelowY + popupHeight <= containerHeight - padding

    val preferredY = when {
        preferAbove && fitsAbove -> rawAboveY.coerceAtLeast(padding)
        !preferAbove && fitsBelow -> rawBelowY
        fitsBelow -> rawBelowY
        fitsAbove -> rawAboveY.coerceAtLeast(padding)
        else -> {
            val minY = padding
            val maxY = (containerHeight - popupHeight - padding).coerceAtLeast(minY)
            (anchorPosition.y - popupHeight / 2f).coerceIn(minY, maxY)
        }
    }

    val y = if (popupHeight <= 0f) {
        (anchorPosition.y - gap).coerceAtLeast(0f)
    } else {
        preferredY
    }

    return Offset(x, y)
}

private fun calculateSelectionAwarePopupPosition(
    anchorPosition: Offset,
    popupSize: IntSize,
    containerSize: IntSize,
    selectionTop: Float,
    selectionBottom: Float,
    preferAbove: Boolean
): Offset {
    val base = calculatePopupPosition(
        anchorPosition = anchorPosition,
        popupSize = popupSize,
        containerSize = containerSize,
        preferAbove = preferAbove
    )
    if (isNotMeasured(containerSize, popupSize)) return base

    val gap = POPUP_GAP_PX
    val padding = POPUP_PADDING_PX
    val popupHeight = popupSize.height.toFloat()
    val selectionTopWithGap = (selectionTop - gap).coerceAtLeast(padding)
    val selectionBottomWithGap = selectionBottom + gap

    val overlapsSelection =
        base.y < selectionBottomWithGap &&
            (base.y + popupHeight) > selectionTopWithGap

    if (!overlapsSelection) return base

    // Compute raw candidate positions, THEN check if they fit, THEN clamp.
    val rawAboveY = selectionTopWithGap - popupHeight
    val rawBelowY = selectionBottomWithGap
    val maxY = (containerSize.height - popupHeight - padding).coerceAtLeast(padding)

    val fitsAbove = rawAboveY >= padding
    val fitsBelow = rawBelowY <= maxY

    val y = when {
        preferAbove && fitsAbove -> rawAboveY.coerceAtLeast(padding)
        !preferAbove && fitsBelow -> rawBelowY
        fitsAbove -> rawAboveY.coerceAtLeast(padding)
        fitsBelow -> rawBelowY
        else -> base.y.coerceIn(padding, maxY)
    }

    return Offset(base.x, y)
}

internal fun calculateSelectionToolbarPosition(
    anchorPosition: Offset,
    popupSize: IntSize,
    containerSize: IntSize,
    selectionTop: Float,
    selectionBottom: Float
): Offset {
    val gap = POPUP_GAP_PX
    val padding = POPUP_PADDING_PX
    val popupHeight = popupSize.height.toFloat()

    val roomAbove = selectionTop - gap - padding
    val roomBelow = containerSize.height.toFloat() - selectionBottom - gap - padding
    val preferAbove = when {
        roomAbove >= popupHeight -> true   // above fits
        roomBelow >= popupHeight -> false  // below fits
        else -> roomAbove >= roomBelow     // neither fits, pick larger side
    }

    return calculateSelectionAwarePopupPosition(
        anchorPosition = anchorPosition,
        popupSize = popupSize,
        containerSize = containerSize,
        selectionTop = selectionTop,
        selectionBottom = selectionBottom,
        preferAbove = preferAbove
    )
}
