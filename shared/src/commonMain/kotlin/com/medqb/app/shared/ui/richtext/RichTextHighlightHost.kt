package com.medqb.app.shared.ui.richtext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.platform.PlatformBackHandler
import com.medqb.app.shared.platform.TextIntentLauncher
import com.medqb.app.shared.platform.setPlainText
import com.medqb.app.shared.ui.richtext.interaction.HighlightTapResult
import com.medqb.app.shared.ui.richtext.interaction.TextSelectionState
import com.medqb.app.shared.ui.richtext.positioning.SelectionHandlePositionProvider
import com.medqb.app.shared.ui.richtext.positioning.SelectionToolbarPositionProvider
import com.medqb.app.shared.ui.richtext.positioning.calculateSelectionRectInAnchor
import com.medqb.app.shared.ui.richtext.toolbar.HighlightFloatingToolbar
import com.medqb.app.shared.ui.richtext.toolbar.HighlightToolbarMode
import kotlinx.coroutines.launch

private val HANDLE_TOUCH_SIZE = 40.dp
private val HANDLE_VISUAL_SIZE = 18.dp

/**
 * Per-block layout, text, and action registration referenced by the active highlight UI session.
 */
@Stable
internal class HighlightBlockRegistration(
    val text: AnnotatedString,
) {
    var layoutResult by mutableStateOf<TextLayoutResult?>(null)
    var textCoordinates: LayoutCoordinates? = null
    var positionInRoot by mutableStateOf(Offset.Zero)
    var dragPosition by mutableStateOf(Offset.Zero)
    var onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit = { _, _, _, _ -> }
    var onHighlightRemove: (highlightId: Long) -> Unit = {}
    var onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit = { _, _ -> }
    var onLinkClick: ((String) -> Unit)? = null
    var onTooltipClick: ((RichTextTooltipContent) -> Unit)? = null
    var onShowSnackbar: suspend (SnackbarMessage) -> Unit = {}
}

/**
 * Single hoisted UI state for text selection or highlight editing across all RichText blocks in a host.
 */
internal sealed interface ActiveHighlightUi {
    val block: HighlightBlockRegistration

    data class Create(
        override val block: HighlightBlockRegistration,
        val startOffset: Int,
        val endOffset: Int,
        val selectedText: String,
        val isDragging: Boolean = false,
        val isMouseSelection: Boolean = false,
    ) : ActiveHighlightUi {
        val hasSelectionRange: Boolean get() = endOffset > startOffset
        val showSelectionToolbar: Boolean get() = hasSelectionRange && !isDragging
        val selectionRange: IntRange get() = startOffset until endOffset
    }

    data class Edit(
        override val block: HighlightBlockRegistration,
        val highlight: TextHighlight,
        val overlappingTooltip: RichTextTooltipContent? = null,
        val overlappingUrl: String? = null,
    ) : ActiveHighlightUi
}

@Stable
internal class RichTextHighlightHostState {
    var activeUi by mutableStateOf<ActiveHighlightUi?>(null)
        private set

    var hostCoordinates: LayoutCoordinates? = null
    var hostPositionInRoot by mutableStateOf(Offset.Zero)

    fun selectionFor(block: HighlightBlockRegistration): ActiveHighlightUi.Create? =
        (activeUi as? ActiveHighlightUi.Create)?.takeIf { it.block === block }

    fun editingFor(block: HighlightBlockRegistration): ActiveHighlightUi.Edit? =
        (activeUi as? ActiveHighlightUi.Edit)?.takeIf { it.block === block }

    fun updateSelection(block: HighlightBlockRegistration, selection: TextSelectionState) {
        if (selection.hasSelectionRange) {
            activeUi = ActiveHighlightUi.Create(
                block = block,
                startOffset = selection.startOffset,
                endOffset = selection.endOffset,
                selectedText = selection.selectedText,
                isDragging = selection.isDragging,
                isMouseSelection = selection.isMouseSelection,
            )
        } else if ((activeUi as? ActiveHighlightUi.Create)?.block === block) {
            activeUi = null
        }
    }

    fun showEditToolbar(block: HighlightBlockRegistration, hit: HighlightTapResult) {
        activeUi = ActiveHighlightUi.Edit(
            block = block,
            highlight = hit.highlight,
            overlappingTooltip = hit.overlappingTooltip,
            overlappingUrl = hit.overlappingUrl,
        )
    }

    fun updateEditingHighlight(block: HighlightBlockRegistration, updated: TextHighlight) {
        val current = activeUi as? ActiveHighlightUi.Edit ?: return
        if (current.block === block) {
            activeUi = current.copy(highlight = updated)
        }
    }

    fun clearIfActive(block: HighlightBlockRegistration) {
        if (activeUi?.block === block) {
            activeUi = null
        }
    }

    fun dismiss() {
        activeUi = null
    }
}

internal val LocalRichTextHighlightHostState = compositionLocalOf<RichTextHighlightHostState?> { null }

@Composable
internal fun rememberRichTextHighlightHostState(vararg keys: Any?): RichTextHighlightHostState =
    remember(*keys) { RichTextHighlightHostState() }

/**
 * Provides a shared [RichTextHighlightHostState] and renders the single selection-handle /
 * floating-toolbar overlay for all highlightable RichText blocks within [content].
 */
@Composable
internal fun RichTextHighlightHost(
    state: RichTextHighlightHostState = rememberRichTextHighlightHostState(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRichTextHighlightHostState provides state) {
        Box(
            modifier = modifier
                .dismissSelectionOnTap(state)
                .onGloballyPositioned { coords ->
                    state.hostCoordinates = coords
                    val pos = coords.localToRoot(Offset.Zero)
                    if (state.hostPositionInRoot != pos) {
                        state.hostPositionInRoot = pos
                    }
                },
        ) {
            content()
            RichTextHighlightOverlay(state = state)
        }
    }
}

private fun Modifier.dismissSelectionOnTap(
    hostState: RichTextHighlightHostState,
): Modifier = pointerInput(hostState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val up = waitForUpOrCancellation()
        if (up != null && !up.isConsumed) {
            hostState.dismiss()
        }
    }
}

@Composable
private fun RichTextHighlightOverlay(state: RichTextHighlightHostState) {
    val activeUi = state.activeUi
    PlatformBackHandler(enabled = activeUi != null) {
        state.dismiss()
    }

    val ui = activeUi ?: return
    val block = ui.block
    val layout = block.layoutResult ?: return
    val text = block.text
    if (text.isEmpty()) return

    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
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

    // Read both snapshot positions so layout shifts (e.g. image load / resize / scroll) reposition the Popup
    val anchorOffset = remember(
        state.hostPositionInRoot,
        block.positionInRoot,
        state.hostCoordinates,
        block.textCoordinates,
    ) {
        textOriginInHost(state.hostCoordinates, block.textCoordinates)
    }

    if (ui is ActiveHighlightUi.Create && ui.hasSelectionRange && !ui.isMouseSelection) {
        val selStart = ui.startOffset.coerceIn(0, text.text.lastIndex)
        val selEnd = ui.endOffset.coerceIn(selStart + 1, text.length)
        val startBox = layout.getBoundingBox(selStart)
        val endBox = layout.getBoundingBox((selEnd - 1).coerceIn(0, text.text.lastIndex))

        val updateHandleSelection: (Int, Int, Boolean) -> Unit = { start, end, dragging ->
            state.updateSelection(
                block = block,
                selection = if (end > start) {
                    TextSelectionState(
                        isSelecting = true,
                        isDragging = dragging,
                        startOffset = start,
                        endOffset = end,
                        selectedText = text.text.substring(start, end),
                        isMouseSelection = false,
                    )
                } else {
                    TextSelectionState()
                }
            )
        }

        SelectionDragHandlePopup(
            blockKey = block,
            isStartHandle = true,
            anchorInText = Offset(startBox.left, startBox.bottom),
            lineCenterY = (startBox.top + startBox.bottom) / 2f,
            text = text.text,
            anchorOffset = anchorOffset,
            currentSelection = { state.selectionFor(block) },
            currentLayoutResult = { block.layoutResult },
            currentTextCoordinates = { block.textCoordinates },
            onSelectionUpdated = updateHandleSelection,
            onDragPositionChanged = { block.dragPosition = it },
        )

        SelectionDragHandlePopup(
            blockKey = block,
            isStartHandle = false,
            anchorInText = Offset(endBox.right, endBox.bottom),
            lineCenterY = (endBox.top + endBox.bottom) / 2f,
            text = text.text,
            anchorOffset = anchorOffset,
            currentSelection = { state.selectionFor(block) },
            currentLayoutResult = { block.layoutResult },
            currentTextCoordinates = { block.textCoordinates },
            onSelectionUpdated = updateHandleSelection,
            onDragPositionChanged = { block.dragPosition = it },
        )
    }

    val activeToolbarMode: HighlightToolbarMode? = when (ui) {
        is ActiveHighlightUi.Create -> if (ui.showSelectionToolbar) {
            HighlightToolbarMode.Create(selectedText = ui.selectedText)
        } else {
            null
        }
        is ActiveHighlightUi.Edit -> HighlightToolbarMode.Edit(
            highlight = ui.highlight,
            overlappingTooltip = ui.overlappingTooltip,
            overlappingUrl = ui.overlappingUrl,
        )
    }

    if (activeToolbarMode != null) {
        val rangeStart: Int
        val rangeEnd: Int
        val belowGap: Int
        when (ui) {
            is ActiveHighlightUi.Create -> {
                rangeStart = ui.startOffset
                rangeEnd = ui.endOffset
                belowGap = if (ui.isMouseSelection) compactBelowGapPx else handleClearanceBelowGapPx
            }
            is ActiveHighlightUi.Edit -> {
                rangeStart = ui.highlight.startOffset
                rangeEnd = ui.highlight.endOffset
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
            belowGap,
            anchorOffset,
        ) {
            SelectionToolbarPositionProvider(
                selectionRectInAnchor = selectionRect,
                edgeMarginPx = edgeMarginPx,
                topSafeMarginPx = topSafeMarginPx,
                bottomSafeMarginPx = bottomSafeMarginPx,
                aboveGapPx = aboveGapPx,
                belowGapPx = belowGap,
                anchorOffset = anchorOffset,
            )
        }

        HighlightAnchoredPopup(
            positionProvider = positionProvider,
            onDismissRequest = state::dismiss,
        ) {
            HighlightFloatingToolbar(
                mode = activeToolbarMode,
                onColorSelect = { chosenColor ->
                    when (activeToolbarMode) {
                        is HighlightToolbarMode.Create -> {
                            val createUi = ui as? ActiveHighlightUi.Create
                            if (createUi != null) {
                                val range = createUi.selectionRange
                                block.onHighlightAdd(range.first, range.last + 1, activeToolbarMode.selectedText, chosenColor)
                            }
                            state.dismiss()
                        }
                        is HighlightToolbarMode.Edit -> {
                            if (chosenColor == activeToolbarMode.highlight.color) {
                                block.onHighlightRemove(activeToolbarMode.highlight.id)
                            } else {
                                block.onHighlightColorChange(activeToolbarMode.highlight.id, chosenColor)
                            }
                            state.dismiss()
                        }
                    }
                },
                onCopy = {
                    val copiedText = activeToolbarMode.text
                    state.dismiss()
                    if (copiedText.isNotBlank()) {
                        coroutineScope.launch {
                            clipboard.setPlainText(AnnotatedString(copiedText))
                        }
                    }
                },
                onLookup = {
                    val query = activeToolbarMode.text
                    state.dismiss()
                    if (query.isNotBlank()) {
                        val opened = TextIntentLauncher.openSelectedText(query)
                        if (!opened) {
                            coroutineScope.launch {
                                block.onShowSnackbar(
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
                        block.onHighlightRemove(activeToolbarMode.highlight.id)
                        state.dismiss()
                    }
                } else null,
                onOpenTooltip = block.onTooltipClick?.let { openTooltip ->
                    (activeToolbarMode as? HighlightToolbarMode.Edit)?.overlappingTooltip?.let { tip ->
                        {
                            state.dismiss()
                            openTooltip(tip)
                        }
                    }
                },
                onOpenLink = block.onLinkClick?.let { openLink ->
                    (activeToolbarMode as? HighlightToolbarMode.Edit)?.overlappingUrl?.let { url ->
                        {
                            state.dismiss()
                            openLink(url)
                        }
                    }
                },
            )
        }
    }
}

private fun textOriginInHost(
    hostCoordinates: LayoutCoordinates?,
    textCoordinates: LayoutCoordinates?,
): Offset {
    if (hostCoordinates == null || textCoordinates == null || !hostCoordinates.isAttached || !textCoordinates.isAttached) {
        return Offset.Zero
    }
    return hostCoordinates.localPositionOf(textCoordinates, Offset.Zero)
}

private fun LayoutCoordinates.safeToScreenOrWindow(offset: Offset): Offset {
    val screen = localToScreen(offset)
    return if (screen.isSpecified) screen else localToWindow(offset)
}

@Composable
private fun SelectionDragHandlePopup(
    blockKey: Any,
    isStartHandle: Boolean,
    anchorInText: Offset,
    lineCenterY: Float,
    text: String,
    anchorOffset: Offset,
    currentSelection: () -> ActiveHighlightUi.Create?,
    currentLayoutResult: () -> TextLayoutResult?,
    currentTextCoordinates: () -> LayoutCoordinates?,
    onSelectionUpdated: (startOffset: Int, endOffset: Int, isDragging: Boolean) -> Unit,
    onDragPositionChanged: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val touchSizePx = remember(density) { with(density) { HANDLE_TOUCH_SIZE.roundToPx() } }
    val visualSizePx = remember(density) { with(density) { HANDLE_VISUAL_SIZE.roundToPx() } }
    val handleColor = MaterialTheme.colorScheme.primary
    val handlePath = remember { Path() }

    val latestSelection by rememberUpdatedState(currentSelection)
    val latestLayoutResult by rememberUpdatedState(currentLayoutResult)
    val latestTextCoordinates by rememberUpdatedState(currentTextCoordinates)
    val latestAnchorInText by rememberUpdatedState(anchorInText)
    val latestLineCenterY by rememberUpdatedState(lineCenterY)
    val latestOnSelectionUpdated by rememberUpdatedState(onSelectionUpdated)
    val latestOnDragPositionChanged by rememberUpdatedState(onDragPositionChanged)

    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestHandleCoordinates by rememberUpdatedState(handleCoordinates)

    val positionProvider = remember(anchorInText, isStartHandle, touchSizePx, visualSizePx, anchorOffset) {
        SelectionHandlePositionProvider(
            anchorInText = anchorInText,
            isStartHandle = isStartHandle,
            touchSizePx = touchSizePx,
            visualSizePx = visualSizePx,
            anchorOffset = anchorOffset,
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
                .pointerInput(blockKey, isStartHandle) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        down.consume()

                        val initialSelection = latestSelection()?.takeIf { it.hasSelectionRange }
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
                        latestOnSelectionUpdated(fixedStart, fixedEnd, true)
                        latestOnDragPositionChanged(Offset(latestAnchorInText.x, latestLineCenterY))

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

                                latestOnDragPositionChanged(pointerInText)
                                val layout = latestLayoutResult() ?: continue
                                val rawOffset = layout.getOffsetForPosition(pointerInText).coerceIn(0, text.length)
                                if (isStartHandle) {
                                    val newStart = rawOffset.coerceIn(0, (fixedEnd - 1).coerceAtLeast(0))
                                    if (newStart != activeStart && newStart < fixedEnd) {
                                        activeStart = newStart
                                        latestOnSelectionUpdated(activeStart, fixedEnd, true)
                                    }
                                } else {
                                    val newEnd = rawOffset.coerceIn((fixedStart + 1).coerceAtMost(text.length), text.length)
                                    if (newEnd != activeEnd && newEnd > fixedStart) {
                                        activeEnd = newEnd
                                        latestOnSelectionUpdated(fixedStart, activeEnd, true)
                                    }
                                }
                            }
                        } finally {
                            latestOnDragPositionChanged(Offset.Zero)
                            if (completedNormally && latestSelection() != null) {
                                val resolvedStart = if (isStartHandle) activeStart else fixedStart
                                val resolvedEnd = if (isStartHandle) fixedEnd else activeEnd
                                latestOnSelectionUpdated(resolvedStart, resolvedEnd, false)
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
