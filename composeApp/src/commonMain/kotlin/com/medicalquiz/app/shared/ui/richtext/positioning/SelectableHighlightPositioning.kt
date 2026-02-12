package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize

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

    val centerX = (startBox.left + endBox.right) / 2f
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
    if (containerSize == IntSize.Zero) return anchorPosition

    val padding = 8f
    val gap = 12f
    val popupWidth = popupSize.width.toFloat()
    val popupHeight = popupSize.height.toFloat()
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()

    val minX = padding
    val maxX = (containerWidth - popupWidth - padding).coerceAtLeast(minX)
    val centeredX = anchorPosition.x - (popupWidth / 2f)
    val x = centeredX.coerceIn(minX, maxX)

    val aboveY = anchorPosition.y - popupHeight - gap
    val belowY = anchorPosition.y + gap

    val fitsAbove = aboveY >= padding
    val fitsBelow = belowY + popupHeight <= containerHeight - padding

    val preferredY = when {
        preferAbove && fitsAbove -> aboveY
        !preferAbove && fitsBelow -> belowY
        fitsBelow -> belowY
        fitsAbove -> aboveY
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
    if (containerSize == IntSize.Zero || popupSize == IntSize.Zero) return base

    val gap = 12f
    val padding = 8f
    val popupHeight = popupSize.height.toFloat()
    val selectionTopWithGap = (selectionTop - gap).coerceAtLeast(padding)
    val selectionBottomWithGap = selectionBottom + gap

    val overlapsSelection =
        base.y < selectionBottomWithGap &&
            (base.y + popupHeight) > selectionTopWithGap

    if (!overlapsSelection) return base

    val aboveY = (selectionTopWithGap - popupHeight).coerceAtLeast(padding)
    val belowY = selectionBottomWithGap
    val maxY = (containerSize.height - popupHeight - padding).coerceAtLeast(padding)

    val fitsAbove = aboveY >= padding
    val fitsBelow = belowY <= maxY

    val y = when {
        preferAbove && fitsAbove -> aboveY
        !preferAbove && fitsBelow -> belowY
        fitsAbove -> aboveY
        fitsBelow -> belowY
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
    val gap = 12f
    val padding = 8f
    val popupHeight = popupSize.height.toFloat()

    val roomAbove = selectionTop - gap - padding
    val roomBelow = containerSize.height.toFloat() - selectionBottom - gap - padding
    val preferAbove = roomAbove >= popupHeight || roomAbove >= roomBelow

    return calculateSelectionAwarePopupPosition(
        anchorPosition = anchorPosition,
        popupSize = popupSize,
        containerSize = containerSize,
        selectionTop = selectionTop,
        selectionBottom = selectionBottom,
        preferAbove = preferAbove
    )
}
