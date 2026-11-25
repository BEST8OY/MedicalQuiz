package com.medqb.app.shared.ui.richtext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.platform.PlatformBackHandler
import com.medqb.app.shared.platform.PlatformKind
import com.medqb.app.shared.platform.TextIntentLauncher
import com.medqb.app.shared.platform.getPlatformKind
import com.medqb.app.shared.platform.setPlainText
import com.medqb.app.shared.ui.richtext.interaction.HighlightTapResult
import com.medqb.app.shared.ui.richtext.interaction.TextSelectionState
import com.medqb.app.shared.ui.richtext.interaction.selectableHighlightGestures
import com.medqb.app.shared.ui.richtext.positioning.SelectionHandlePositionProvider
import com.medqb.app.shared.ui.richtext.positioning.SelectionToolbarPositionProvider
import com.medqb.app.shared.ui.richtext.positioning.calculateSelectionRectInAnchor
import com.medqb.app.shared.ui.richtext.styling.applyHighlightsToText
import com.medqb.app.shared.ui.richtext.styling.applySelectionToText
import com.medqb.app.shared.ui.richtext.toolbar.HighlightFloatingToolbar
import com.medqb.app.shared.ui.richtext.toolbar.HighlightToolbarMode
import kotlinx.coroutines.launch

private val ANDROID_LONG_PRESS_DRAG_HYSTERESIS = 7.dp
private val DESKTOP_LONG_PRESS_DRAG_HYSTERESIS = 5.dp
private val HANDLE_TOUCH_SIZE = 40.dp
private val HANDLE_VISUAL_SIZE = 18.dp

private class SelectableHighlightTextState {
    var selectionState by mutableStateOf(TextSelectionState())
    var editingHighlight by mutableStateOf<HighlightTapResult?>(null)
    var layoutResult by mutableStateOf<TextLayoutResult?>(null)
    var textCoordinates by mutableStateOf<LayoutCoordinates?>(null)
    var dragPosition by mutableStateOf(Offset.Zero)
}

@Composable
internal fun SelectableHighlightText(
    text: AnnotatedString,
    highlights: List<TextHighlight>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.scaledBy(LocalRichTextScale.current.proseScale),
    color: Color = Color.Unspecified,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)? = null,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember(text) { SelectableHighlightTextState() }
    val ownerToken = remember { Any() }
    val selectionCoordinator = LocalRichTextSelectionCoordinator.current
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val dismissToolbar = remember(state) {
        {
            state.selectionState = TextSelectionState()
            state.editingHighlight = null
        }
    }

    val hasActiveUi = state.selectionState.hasSelectionRange || state.editingHighlight != null
    PlatformBackHandler(enabled = hasActiveUi) {
        dismissToolbar()
    }

    DisposableEffect(selectionCoordinator, hasActiveUi, ownerToken) {
        if (hasActiveUi) {
            selectionCoordinator?.claim(ownerToken, dismissToolbar)
        } else {
            selectionCoordinator?.release(ownerToken)
        }
        onDispose {
            selectionCoordinator?.release(ownerToken)
        }
    }

    // Reconcile editingHighlight if the backing highlights list updates (e.g., deleted or recolored)
    LaunchedEffect(highlights) {
        val currentEditing = state.editingHighlight ?: return@LaunchedEffect
        val updated = highlights.firstOrNull { it.id == currentEditing.highlight.id }
        if (updated == null) {
            state.editingHighlight = null
        } else if (updated != currentEditing.highlight) {
            state.editingHighlight = currentEditing.copy(highlight = updated)
        }
    }

    val platformKind = remember { getPlatformKind() }
    val longPressDragHysteresis = remember(platformKind) {
        when (platformKind) {
            PlatformKind.Android -> ANDROID_LONG_PRESS_DRAG_HYSTERESIS
            PlatformKind.Desktop -> DESKTOP_LONG_PRESS_DRAG_HYSTERESIS
        }
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    val longPressDragHysteresisPx = remember(density, longPressDragHysteresis) {
        with(density) { longPressDragHysteresis.toPx() }
    }
    val safeTopPx = safeDrawing.getTop(density)
    val safeBottomPx = safeDrawing.getBottom(density)
    val safeLeftPx = safeDrawing.getLeft(density, layoutDirection)
    val safeRightPx = safeDrawing.getRight(density, layoutDirection)
    val edgeMarginPx = maxOf(safeLeftPx, safeRightPx) + with(density) { 12.dp.roundToPx() }
    val topSafeMarginPx = safeTopPx + with(density) { 8.dp.roundToPx() }
    val bottomSafeMarginPx = safeBottomPx + with(density) { 8.dp.roundToPx() }
    val aboveGapPx = remember(density) { with(density) { 10.dp.roundToPx() } }
    val handleClearanceBelowGapPx = remember(density) { with(density) { 36.dp.roundToPx() } }
    val compactBelowGapPx = remember(density) { with(density) { 10.dp.roundToPx() } }

    val highlightedText = remember(text, highlights, isDarkTheme) {
        applyHighlightsToText(text, highlights, isDarkTheme = isDarkTheme)
    }
    val selectionColor = MaterialTheme.colorScheme.primaryContainer
    val displayText = remember(highlightedText, state.selectionState, selectionColor) {
        if (state.selectionState.hasSelectionRange) {
            applySelectionToText(highlightedText, state.selectionState.selectionRange, selectionColor)
        } else {
            highlightedText
        }
    }

    val resolvedFontSize = if (textStyle.fontSize == TextUnit.Unspecified) {
        MaterialTheme.typography.bodyMedium.fontSize
    } else {
        textStyle.fontSize
    }
    val resolvedLineHeight = if (textStyle.lineHeight == TextUnit.Unspecified) {
        resolvedFontSize * LINE_HEIGHT_MULTIPLIER
    } else {
        textStyle.lineHeight
    }

    Box(modifier = modifier) {
        BasicText(
            text = displayText,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { state.textCoordinates = it }
                .then(
                    if (state.selectionState.isDragging) {
                        Modifier.platformSelectionMagnifier(
                            sourceCenter = {
                                val pos = state.dragPosition
                                val layout = state.layoutResult
                                if (layout != null && text.isNotEmpty()) {
                                    val line = layout.getLineForVerticalPosition(pos.y)
                                    val lineCenterY = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
                                    val lo = layout.getLineLeft(line)
                                    val hi = layout.getLineRight(line)
                                    val clampedX = if (hi > lo) pos.x.coerceIn(lo, hi) else pos.x
                                    Offset(x = clampedX, y = lineCenterY)
                                } else {
                                    pos
                                }
                            },
                        )
                    } else {
                        Modifier
                    }
                )
                .selectableHighlightGestures(
                    text = text,
                    highlights = highlights,
                    longPressDragHysteresisPx = longPressDragHysteresisPx,
                    currentLayoutResult = { state.layoutResult },
                    currentSelectionState = { state.selectionState },
                    setSelectionState = { state.selectionState = it },
                    setEditingHighlight = { state.editingHighlight = it },
                    setDragPosition = { state.dragPosition = it },
                    onDismissOthers = { selectionCoordinator?.dismissOthers(ownerToken) },
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick
                ),
            style = textStyle.copy(
                color = if (color != Color.Unspecified) color else if (textStyle.color != Color.Unspecified) textStyle.color else LocalContentColor.current,
                lineHeight = resolvedLineHeight,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                ),
                fontSize = resolvedFontSize,
            ),
            onTextLayout = { state.layoutResult = it }
        )

        val layout = state.layoutResult
        if (layout != null && text.isNotEmpty() && state.selectionState.hasSelectionRange && !state.selectionState.isMouseSelection) {
            val selStart = state.selectionState.startOffset.coerceIn(0, text.text.lastIndex)
            val selEnd = state.selectionState.endOffset.coerceIn(selStart + 1, text.length)
            val startBox = layout.getBoundingBox(selStart)
            val endBox = layout.getBoundingBox((selEnd - 1).coerceIn(0, text.text.lastIndex))

            SelectionDragHandlePopup(
                isStartHandle = true,
                anchorInText = Offset(startBox.left, startBox.bottom),
                lineCenterY = (startBox.top + startBox.bottom) / 2f,
                text = text.text,
                currentSelectionState = { state.selectionState },
                currentLayoutResult = { state.layoutResult },
                currentTextCoordinates = { state.textCoordinates },
                onSelectionUpdated = { state.selectionState = it },
                onDragPositionChanged = { state.dragPosition = it },
            )

            SelectionDragHandlePopup(
                isStartHandle = false,
                anchorInText = Offset(endBox.right, endBox.bottom),
                lineCenterY = (endBox.top + endBox.bottom) / 2f,
                text = text.text,
                currentSelectionState = { state.selectionState },
                currentLayoutResult = { state.layoutResult },
                currentTextCoordinates = { state.textCoordinates },
                onSelectionUpdated = { state.selectionState = it },
                onDragPositionChanged = { state.dragPosition = it },
            )
        }

        val activeToolbarMode: HighlightToolbarMode? = when {
            state.selectionState.showSelectionToolbar ->
                HighlightToolbarMode.Create(selectedText = state.selectionState.selectedText)
            state.editingHighlight != null -> state.editingHighlight?.let { hit ->
                HighlightToolbarMode.Edit(
                    highlight = hit.highlight,
                    overlappingTooltip = hit.overlappingTooltip,
                    overlappingUrl = hit.overlappingUrl,
                )
            }
            else -> null
        }

        if (activeToolbarMode != null && layout != null && text.isNotEmpty()) {
            val rangeStart: Int
            val rangeEnd: Int
            val belowGap: Int
            when (activeToolbarMode) {
                is HighlightToolbarMode.Create -> {
                    rangeStart = state.selectionState.startOffset
                    rangeEnd = state.selectionState.endOffset
                    belowGap = if (state.selectionState.isMouseSelection) compactBelowGapPx else handleClearanceBelowGapPx
                }
                is HighlightToolbarMode.Edit -> {
                    rangeStart = activeToolbarMode.highlight.startOffset
                    rangeEnd = activeToolbarMode.highlight.endOffset
                    belowGap = compactBelowGapPx
                }
            }

            val selectionRect = remember(layout, text.length, rangeStart, rangeEnd) {
                calculateSelectionRectInAnchor(
                    layoutResult = layout,
                    textLength = text.length,
                    startOffset = rangeStart,
                    endOffset = rangeEnd,
                )
            }
            val positionProvider = remember(
                selectionRect,
                edgeMarginPx,
                topSafeMarginPx,
                bottomSafeMarginPx,
                aboveGapPx,
                belowGap
            ) {
                SelectionToolbarPositionProvider(
                    selectionRectInAnchor = selectionRect,
                    edgeMarginPx = edgeMarginPx,
                    topSafeMarginPx = topSafeMarginPx,
                    bottomSafeMarginPx = bottomSafeMarginPx,
                    aboveGapPx = aboveGapPx,
                    belowGapPx = belowGap,
                )
            }

            HighlightAnchoredPopup(
                positionProvider = positionProvider,
                onDismissRequest = dismissToolbar,
            ) {
                HighlightFloatingToolbar(
                    mode = activeToolbarMode,
                    onColorSelect = { chosenColor ->
                        when (activeToolbarMode) {
                            is HighlightToolbarMode.Create -> {
                                val range = state.selectionState.selectionRange
                                onHighlightAdd(range.first, range.last + 1, activeToolbarMode.selectedText, chosenColor)
                                state.selectionState = TextSelectionState()
                            }
                            is HighlightToolbarMode.Edit -> {
                                if (chosenColor == activeToolbarMode.highlight.color) {
                                    onHighlightRemove(activeToolbarMode.highlight.id)
                                } else {
                                    onHighlightColorChange(activeToolbarMode.highlight.id, chosenColor)
                                }
                                state.editingHighlight = null
                            }
                        }
                    },
                    onCopy = {
                        val copiedText = activeToolbarMode.text
                        dismissToolbar()
                        if (copiedText.isNotBlank()) {
                            coroutineScope.launch {
                                clipboard.setPlainText(AnnotatedString(copiedText))
                            }
                        }
                    },
                    onLookup = {
                        val query = activeToolbarMode.text
                        dismissToolbar()
                        if (query.isNotBlank()) {
                            val opened = TextIntentLauncher.openSelectedText(query)
                            if (!opened) {
                                coroutineScope.launch {
                                    onShowSnackbar(
                                        SnackbarMessage.Action(
                                            message = "No compatible app found",
                                            actionLabel = "Copy",
                                            onActionPerformed = {
                                                clipboard.setPlainText(AnnotatedString(query))
                                            },
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onDelete = if (activeToolbarMode is HighlightToolbarMode.Edit) {
                        {
                            onHighlightRemove(activeToolbarMode.highlight.id)
                            state.editingHighlight = null
                        }
                    } else null,
                    onOpenTooltip = if (onTooltipClick != null) {
                        (activeToolbarMode as? HighlightToolbarMode.Edit)?.overlappingTooltip?.let { tip ->
                            {
                                dismissToolbar()
                                onTooltipClick(tip)
                            }
                        }
                    } else null,
                    onOpenLink = if (onLinkClick != null) {
                        (activeToolbarMode as? HighlightToolbarMode.Edit)?.overlappingUrl?.let { url ->
                            {
                                dismissToolbar()
                                onLinkClick(url)
                            }
                        }
                    } else null,
                )
            }
        }
    }
}

private fun LayoutCoordinates.safeToScreenOrWindow(offset: Offset): Offset {
    val screen = localToScreen(offset)
    return if (screen.isSpecified) screen else localToWindow(offset)
}

@Composable
private fun SelectionDragHandlePopup(
    isStartHandle: Boolean,
    anchorInText: Offset,
    lineCenterY: Float,
    text: String,
    currentSelectionState: () -> TextSelectionState,
    currentLayoutResult: () -> TextLayoutResult?,
    currentTextCoordinates: () -> LayoutCoordinates?,
    onSelectionUpdated: (TextSelectionState) -> Unit,
    onDragPositionChanged: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val touchSizePx = remember(density) { with(density) { HANDLE_TOUCH_SIZE.roundToPx() } }
    val visualSizePx = remember(density) { with(density) { HANDLE_VISUAL_SIZE.roundToPx() } }
    val handleColor = MaterialTheme.colorScheme.primary
    val handlePath = remember { Path() }

    val latestSelectionState by rememberUpdatedState(currentSelectionState)
    val latestLayoutResult by rememberUpdatedState(currentLayoutResult)
    val latestTextCoordinates by rememberUpdatedState(currentTextCoordinates)
    val latestAnchorInText by rememberUpdatedState(anchorInText)
    val latestLineCenterY by rememberUpdatedState(lineCenterY)

    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestHandleCoordinates by rememberUpdatedState(handleCoordinates)

    val positionProvider = remember(anchorInText, isStartHandle, touchSizePx, visualSizePx) {
        SelectionHandlePositionProvider(
            anchorInText = anchorInText,
            isStartHandle = isStartHandle,
            touchSizePx = touchSizePx,
            visualSizePx = visualSizePx,
        )
    }

    val description = if (isStartHandle) "Start selection handle" else "End selection handle"
    val tag = if (isStartHandle) "selection_handle_start" else "selection_handle_end"

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(HANDLE_TOUCH_SIZE)
                .onGloballyPositioned { handleCoordinates = it }
                .testTag(tag)
                .semantics { contentDescription = description }
                .pointerInput(text, isStartHandle) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        down.consume()

                        val initialSelection = latestSelectionState().takeIf { it.hasSelectionRange }
                            ?: return@awaitEachGesture
                        val fixedStart = initialSelection.startOffset
                        val fixedEnd = initialSelection.endOffset

                        // Capture grab offset in screen/window space relative to the text aim line,
                        // so subsequent handle repositioning causes zero coordinate feedback jitter.
                        val hCoords = latestHandleCoordinates
                        val tCoords = latestTextCoordinates()
                        val grabOffset = if (hCoords != null && tCoords != null && hCoords.isAttached && tCoords.isAttached) {
                            val downInText = hCoords.safeToScreenOrWindow(down.position) - tCoords.safeToScreenOrWindow(Offset.Zero)
                            Offset(
                                x = downInText.x - latestAnchorInText.x,
                                y = downInText.y - latestLineCenterY,
                            )
                        } else {
                            Offset(0f, touchSizePx / 2f)
                        }

                        var activeStart = fixedStart
                        var activeEnd = fixedEnd
                        var completedNormally = false
                        onSelectionUpdated(initialSelection.copy(isDragging = true))
                        onDragPositionChanged(Offset(latestAnchorInText.x, latestLineCenterY))

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    completedNormally = true
                                    break
                                }
                                change.consume()

                                val activeHCoords = latestHandleCoordinates
                                val activeTCoords = latestTextCoordinates()
                                val pointerInText = if (activeHCoords != null && activeTCoords != null && activeHCoords.isAttached && activeTCoords.isAttached) {
                                    val ptrInTextRaw = activeHCoords.safeToScreenOrWindow(change.position) - activeTCoords.safeToScreenOrWindow(Offset.Zero)
                                    ptrInTextRaw - grabOffset
                                } else {
                                    continue
                                }

                                onDragPositionChanged(pointerInText)
                                val layout = latestLayoutResult() ?: continue
                                val rawOffset = layout.getOffsetForPosition(pointerInText).coerceIn(0, text.length)
                                if (isStartHandle) {
                                    val newStart = rawOffset.coerceIn(0, (fixedEnd - 1).coerceAtLeast(0))
                                    if (newStart != activeStart && newStart < fixedEnd) {
                                        activeStart = newStart
                                        onSelectionUpdated(
                                            TextSelectionState(
                                                isSelecting = true,
                                                isDragging = true,
                                                startOffset = activeStart,
                                                endOffset = fixedEnd,
                                                selectedText = text.substring(activeStart, fixedEnd),
                                                isMouseSelection = false,
                                            )
                                        )
                                    }
                                } else {
                                    val newEnd = rawOffset.coerceIn((fixedStart + 1).coerceAtMost(text.length), text.length)
                                    if (newEnd != activeEnd && newEnd > fixedStart) {
                                        activeEnd = newEnd
                                        onSelectionUpdated(
                                            TextSelectionState(
                                                isSelecting = true,
                                                isDragging = true,
                                                startOffset = fixedStart,
                                                endOffset = activeEnd,
                                                selectedText = text.substring(fixedStart, activeEnd),
                                                isMouseSelection = false,
                                            )
                                        )
                                    }
                                }
                            }
                        } finally {
                            onDragPositionChanged(Offset.Zero)
                            if (completedNormally && latestSelectionState().isSelecting) {
                                val resolvedStart = if (isStartHandle) activeStart else fixedStart
                                val resolvedEnd = if (isStartHandle) fixedEnd else activeEnd
                                onSelectionUpdated(
                                    if (resolvedStart < resolvedEnd) {
                                        TextSelectionState(
                                            isSelecting = true,
                                            isDragging = false,
                                            startOffset = resolvedStart,
                                            endOffset = resolvedEnd,
                                            selectedText = text.substring(resolvedStart, resolvedEnd),
                                            isMouseSelection = false,
                                        )
                                    } else {
                                        TextSelectionState()
                                    }
                                )
                            }
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.size(HANDLE_VISUAL_SIZE)) {
                val w = size.width
                val h = size.height
                val radius = w / 2f
                handlePath.rewind()
                if (isStartHandle) {
                    handlePath.moveTo(w, 0f)
                    handlePath.lineTo(radius, 0f)
                    handlePath.arcTo(
                        rect = Rect(0f, 0f, w, h),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = -270f,
                        forceMoveTo = false
                    )
                    handlePath.close()
                } else {
                    handlePath.moveTo(0f, 0f)
                    handlePath.lineTo(radius, 0f)
                    handlePath.arcTo(
                        rect = Rect(0f, 0f, w, h),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 270f,
                        forceMoveTo = false
                    )
                    handlePath.close()
                }
                drawPath(path = handlePath, color = handleColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HighlightAnchoredPopup(
    positionProvider: SelectionToolbarPositionProvider,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        onDismissRequest = onDismissRequest,
    ) {
        val motionScheme = MaterialTheme.motionScheme
        val visibleState = remember {
            MutableTransitionState(false).apply { targetState = true }
        }
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(motionScheme.fastEffectsSpec()) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = motionScheme.fastSpatialSpec(),
                ),
        ) {
            content()
        }
    }
}
