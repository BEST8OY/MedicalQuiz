package com.medqb.app.shared.ui.richtext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.platform.TextIntentLauncher
import com.medqb.app.shared.domain.SnackbarMessage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class SelectableHighlightTextState(initialText: String) {
    val textFieldState = TextFieldState(initialText)
    var editingHighlight by mutableStateOf<TextHighlight?>(null)
    var editPopupAnchor by mutableStateOf(Offset.Zero)
    var layoutResult by mutableStateOf<TextLayoutResult?>(null)
    var containerSize by mutableStateOf(IntSize.Zero)
    var isDragging by mutableStateOf(false)
}

@Composable
internal fun SelectableHighlightText(
    text: androidx.compose.ui.text.AnnotatedString,
    highlights: List<TextHighlight>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.scaledBy(LocalRichTextScale.current.proseScale),
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
    onTooltipClick: ((RichTextTooltipContent) -> Unit)? = null,
    onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember { SelectableHighlightTextState(text.text) }

    // Fresh highlights reference for snapshotFlow without restarting LaunchedEffect
    val currentHighlights = rememberUpdatedState(highlights)

    // Sync external text changes
    LaunchedEffect(text.text) {
        state.textFieldState.edit {
            val currentText = toString()
            if (currentText != text.text) {
                replace(0, currentText.length, text.text)
            }
        }
    }

    // Suppress default context menu
    val emptyToolbar = remember {
        object : TextToolbar {
            override fun showMenu(
                rect: androidx.compose.ui.geometry.Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) { /* no-op */ }

            override fun hide() { /* no-op */ }

            override val status: TextToolbarStatus = TextToolbarStatus.Hidden
        }
    }

    // Dismiss edit popup when highlights change (color change, add, remove)
    LaunchedEffect(highlights) {
        state.editingHighlight = null
    }

    // Observe selection + highlights via snapshotFlow.
    // Reading currentHighlights.value ensures the collector sees the latest list
    // without restarting the LaunchedEffect (which caused toolbar reappear bugs).
    LaunchedEffect(state.textFieldState, text.text) {
        snapshotFlow {
            state.textFieldState.selection to currentHighlights.value
        }.collect { (selection, highlightsNow) ->
            if (!selection.collapsed) {
                state.editingHighlight = null
            } else if (highlightsNow.isNotEmpty()) {
                val offset = selection.min
                val layout = state.layoutResult
                if (layout != null && offset in 0 until text.length) {
                    val tappedHighlight = highlightsNow.firstOrNull { h ->
                        val start = h.startOffset.coerceIn(0, text.length)
                        val endExclusive = h.endOffset.coerceIn(start, text.length)
                        offset in start until endExclusive
                    }
                    if (tappedHighlight != null) {
                        state.editingHighlight = tappedHighlight
                        state.editPopupAnchor = calculateRangeAnchor(
                            layoutResult = layout,
                            textLength = text.length,
                            startOffset = tappedHighlight.startOffset,
                            endOffset = tappedHighlight.endOffset,
                            fallbackAnchor = Offset.Zero
                        )
                    }
                }
            }
        }
    }

    fun dismissToolbar() {
        state.textFieldState.edit { placeCursorAtEnd() }
    }

    val bodyMediumFontSize = MaterialTheme.typography.bodyMedium.fontSize
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val resolvedTextStyle = remember(textStyle, bodyMediumFontSize, onSurfaceColor) {
        val fontSize = if (textStyle.fontSize == androidx.compose.ui.unit.TextUnit.Unspecified) {
            bodyMediumFontSize
        } else {
            textStyle.fontSize
        }
        textStyle.copy(
            color = onSurfaceColor,
            fontSize = fontSize,
            lineHeight = fontSize * 1.375f
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { state.containerSize = it }
            // Track pointer down/up to know when user is dragging selection
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    state.isDragging = true
                    // Wait for all pointers to be released
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    state.isDragging = false
                }
            }
    ) {
        val highlightColors = remember(highlights) {
            highlights.map { h ->
                h.color.toComposeColor().copy(alpha = 0.4f)
            }
        }

        CompositionLocalProvider(LocalTextToolbar provides emptyToolbar) {
            BasicTextField(
                state = state.textFieldState,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val layout = state.layoutResult ?: return@drawBehind
                        highlights.forEachIndexed { index, highlight ->
                            val start = highlight.startOffset.coerceIn(0, text.length)
                            val endExclusive = highlight.endOffset.coerceIn(start, text.length)
                            if (start >= endExclusive) return@forEachIndexed

                            val color = highlightColors.getOrNull(index) ?: return@forEachIndexed

                            val startLine = layout.getLineForOffset(start)
                            val endLine = layout.getLineForOffset((endExclusive - 1).coerceAtLeast(0))

                            for (line in startLine..endLine) {
                                val lineTop = layout.getLineTop(line)
                                val lineBottom = layout.getLineBottom(line)
                                val lineHeight = lineBottom - lineTop

                                val left = if (line == startLine) {
                                    layout.getHorizontalPosition(start, true)
                                } else {
                                    0f
                                }
                                val right = if (line == endLine) {
                                    layout.getHorizontalPosition(endExclusive, true)
                                } else {
                                    // getLineEnd(line, false) = offset OF last char on line
                                    // (not after line break like getLineEnd(line, true))
                                    val lastCharOffset = layout.getLineEnd(line, false) - 1
                                    if (lastCharOffset >= 0) {
                                        layout.getHorizontalPosition(lastCharOffset, true)
                                    } else {
                                        size.width
                                    }
                                }

                                if (right > left) {
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(left, lineTop),
                                        size = Size(right - left, lineHeight)
                                    )
                                }
                            }
                        }
                    },
                readOnly = true,
                textStyle = resolvedTextStyle,
                onTextLayout = { textLayoutResultProvider ->
                    val result = textLayoutResultProvider()
                    if (result != null) {
                        state.layoutResult = result
                    }
                }
            )
        }

        // Selection Toolbar Popup — only show when selection is done (not dragging)
        SelectionToolbarPopup(
            textFieldState = state.textFieldState,
            isDragging = state.isDragging,
            textLength = text.length,
            text = text.text,
            layoutResult = state.layoutResult,
            containerSize = state.containerSize,
            onDismiss = { dismissToolbar() },
            onCopy = { selectedText ->
                if (selectedText.isNotBlank()) {
                    clipboard.setPlainText(androidx.compose.ui.text.AnnotatedString(selectedText))
                }
                dismissToolbar()
            },
            onOpenExternal = { selectedText ->
                if (selectedText.isNotBlank()) {
                    val opened = TextIntentLauncher.openSelectedText(selectedText)
                    if (!opened) {
                        coroutineScope.launch {
                            onShowSnackbar(
                                SnackbarMessage.Action(
                                    message = "No compatible app found",
                                    actionLabel = "Copy",
                                    onActionPerformed = {
                                        if (selectedText.isNotBlank()) {
                                            clipboard.setPlainText(
                                                androidx.compose.ui.text.AnnotatedString(selectedText)
                                            )
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
                dismissToolbar()
            },
            onHighlight = { start, end, selected, color ->
                onHighlightAdd(start, end, selected, color)
                dismissToolbar()
            }
        )

        // Highlight Edit Popup
        HighlightEditPopupContainer(
            editingHighlight = state.editingHighlight,
            anchorPosition = state.editPopupAnchor,
            containerSize = state.containerSize,
            onDismiss = { state.editingHighlight = null },
            onHighlightColorChange = onHighlightColorChange,
            onHighlightRemove = onHighlightRemove
        )
    }
}

@Composable
private fun SelectionToolbarPopup(
    textFieldState: TextFieldState,
    isDragging: Boolean,
    textLength: Int,
    text: String,
    layoutResult: TextLayoutResult?,
    containerSize: IntSize,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onHighlight: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
) {
    val selection = textFieldState.selection
    if (selection.collapsed || layoutResult == null || isDragging) return

    // Use unconstrained height for positioning so toolbar isn't limited to paragraph bounds
    // The Popup itself renders in a separate window, so this only affects positioning math
    val positioningContainerSize = IntSize(containerSize.width, Int.MAX_VALUE / 2)

    val safeTextLength = textLength.coerceAtLeast(1)
    val normalizedStart = selection.min.coerceIn(0, safeTextLength - 1)
    val normalizedEndExclusive = selection.max.coerceIn(normalizedStart + 1, safeTextLength)

    val selectedText = remember(text, normalizedStart, normalizedEndExclusive) {
        if (normalizedStart < normalizedEndExclusive && normalizedEndExclusive <= text.length) {
            text.substring(normalizedStart, normalizedEndExclusive)
        } else ""
    }

    var popupSize by remember { mutableStateOf(IntSize.Zero) }

    val toolbarPosition = remember(
        positioningContainerSize,
        popupSize,
        layoutResult,
        normalizedStart,
        normalizedEndExclusive
    ) {
        val startLine = layoutResult.getLineForOffset(normalizedStart)
        val endLine = layoutResult.getLineForOffset((normalizedEndExclusive - 1).coerceAtLeast(0))
        val selectionTop = layoutResult.getLineTop(minOf(startLine, endLine))
        val selectionBottom = layoutResult.getLineBottom(maxOf(startLine, endLine))

        val anchorPosition = calculateRangeAnchor(
            layoutResult = layoutResult,
            textLength = textLength,
            startOffset = normalizedStart,
            endOffset = normalizedEndExclusive,
            fallbackAnchor = Offset.Zero
        )

        calculateSelectionToolbarPosition(
            anchorPosition = anchorPosition,
            popupSize = popupSize,
            containerSize = positioningContainerSize,
            selectionTop = selectionTop,
            selectionBottom = selectionBottom
        )
    }

    Popup(
        alignment = androidx.compose.ui.Alignment.TopStart,
        offset = IntOffset(
            x = toolbarPosition.x.roundToInt(),
            y = toolbarPosition.y.roundToInt()
        ),
        // Non-focusable so focus stays on text field and selection handles remain visible
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        onDismissRequest = onDismiss
    ) {
        val motionScheme = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                    slideInVertically(motionScheme.defaultSpatialSpec()) { -it / 4 }
        ) {
            Box(
                modifier = Modifier.onSizeChanged { popupSize = it }
            ) {
                SelectionToolbar(
                    selectedText = selectedText,
                    onCopy = { onCopy(selectedText) },
                    onOpenExternal = { onOpenExternal(selectedText) },
                    onHighlight = { color ->
                        onHighlight(selection.min, selection.max, selectedText, color)
                    }
                )
            }
        }
    }
}

@Composable
private fun HighlightEditPopupContainer(
    editingHighlight: TextHighlight?,
    anchorPosition: Offset,
    containerSize: IntSize,
    onDismiss: () -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
) {
    if (editingHighlight == null) return

    var popupSize by remember { mutableStateOf(IntSize.Zero) }

    val popupPosition = remember(
        anchorPosition,
        containerSize,
        popupSize
    ) {
        calculatePopupPosition(
            anchorPosition = anchorPosition,
            popupSize = popupSize,
            containerSize = containerSize,
            preferAbove = true
        )
    }

    Popup(
        alignment = androidx.compose.ui.Alignment.TopStart,
        offset = IntOffset(
            x = popupPosition.x.roundToInt(),
            y = popupPosition.y.roundToInt()
        ),
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        onDismissRequest = onDismiss
    ) {
        val motionScheme = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                    slideInVertically(motionScheme.defaultSpatialSpec()) { -it / 4 }
        ) {
            Box(
                modifier = Modifier.onSizeChanged { popupSize = it }
            ) {
                HighlightEditPopup(
                    highlight = editingHighlight,
                    onColorChange = { color ->
                        onHighlightColorChange(editingHighlight.id, color)
                        onDismiss()
                    },
                    onDelete = {
                        onHighlightRemove(editingHighlight.id)
                        onDismiss()
                    }
                )
            }
        }
    }
}
