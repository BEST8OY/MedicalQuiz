package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.medicalquiz.app.shared.data.models.HighlightColor
import com.medicalquiz.app.shared.data.models.TextHighlight
import com.medicalquiz.app.shared.platform.PlatformKind
import com.medicalquiz.app.shared.platform.TextIntentLauncher
import com.medicalquiz.app.shared.platform.getPlatformKind
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ANDROID_LONG_PRESS_DRAG_HYSTERESIS = 7.dp
private val ANDROID_HANDLE_DRAG_HYSTERESIS = 7.dp
private val DESKTOP_LONG_PRESS_DRAG_HYSTERESIS = 5.dp
private val DESKTOP_HANDLE_DRAG_HYSTERESIS = 5.dp

@Composable
fun SelectableHighlightText(
    text: AnnotatedString,
    highlights: List<TextHighlight>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.scaledBy(LocalRichTextScale.current.proseScale),
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var lastExternalOpenText by remember { mutableStateOf("") }
    var selectionState by remember { mutableStateOf(TextSelectionState()) }
    var editingHighlight by remember { mutableStateOf<TextHighlight?>(null) }
    var editPopupAnchor by remember { mutableStateOf(Offset.Zero) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var selectionPopupSize by remember { mutableStateOf(IntSize.Zero) }
    var editPopupSize by remember { mutableStateOf(IntSize.Zero) }

    val platformKind = remember { getPlatformKind() }
    val longPressDragHysteresis = remember(platformKind) {
        when (platformKind) {
            PlatformKind.Android -> ANDROID_LONG_PRESS_DRAG_HYSTERESIS
            PlatformKind.Desktop -> DESKTOP_LONG_PRESS_DRAG_HYSTERESIS
        }
    }
    val handleDragHysteresis = remember(platformKind) {
        when (platformKind) {
            PlatformKind.Android -> ANDROID_HANDLE_DRAG_HYSTERESIS
            PlatformKind.Desktop -> DESKTOP_HANDLE_DRAG_HYSTERESIS
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val longPressDragHysteresisPx = remember(density) {
        with(density) { longPressDragHysteresis.toPx() }
    }
    val handleDragHysteresisPx = remember(density) {
        with(density) { handleDragHysteresis.toPx() }
    }

    val highlightedText = remember(text, highlights) {
        applyHighlightsToText(text, highlights)
    }
    val highlightsById = remember(highlights) {
        highlights.associateBy { it.id }
    }

    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val displayText = remember(highlightedText, selectionState, selectionColor) {
        if (selectionState.isSelecting) {
            applySelectionToText(highlightedText, selectionState.selectionRange, selectionColor)
        } else {
            highlightedText
        }
    }

    Box(
        modifier = modifier.onSizeChanged { containerSize = it }
    ) {
        BasicText(
            text = displayText,
            modifier = Modifier
                .fillMaxWidth()
                .selectableHighlightGestures(
                    text = text,
                    highlightedText = highlightedText,
                    highlights = highlights,
                    highlightsById = highlightsById,
                    longPressDragHysteresisPx = longPressDragHysteresisPx,
                    currentLayoutResult = { layoutResult },
                    currentSelectionState = { selectionState },
                    setSelectionState = { selectionState = it },
                    setEditingHighlight = { editingHighlight = it },
                    setEditPopupAnchor = { editPopupAnchor = it },
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick
                ),
            style = textStyle.copy(
                color = if (textStyle.color == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    textStyle.color
                },
                lineHeight = (if (textStyle.fontSize == androidx.compose.ui.unit.TextUnit.Unspecified) {
                    MaterialTheme.typography.bodyMedium.fontSize
                } else {
                    textStyle.fontSize
                }) * 1.375f,
                fontSize = if (textStyle.fontSize == androidx.compose.ui.unit.TextUnit.Unspecified) {
                    MaterialTheme.typography.bodyMedium.fontSize
                } else {
                    textStyle.fontSize
                },
            ),
            onTextLayout = { layoutResult = it }
        )

        if (selectionState.hasSelectionRange) {
            val safeTextLength = text.length.coerceAtLeast(1)
            val normalizedStart = minOf(selectionState.startOffset, selectionState.endOffset)
                .coerceIn(0, safeTextLength - 1)
            val normalizedEndExclusive = maxOf(selectionState.startOffset, selectionState.endOffset)
                .coerceIn(normalizedStart + 1, safeTextLength)

            if (selectionState.showSelectionToolbar) {
                val toolbarPosition = remember(
                    selectionState.anchorPosition,
                    containerSize,
                    selectionPopupSize,
                    layoutResult,
                    normalizedStart,
                    normalizedEndExclusive
                ) {
                    val layout = layoutResult
                    if (layout == null) {
                        calculatePopupPosition(
                            anchorPosition = selectionState.anchorPosition,
                            popupSize = selectionPopupSize,
                            containerSize = containerSize,
                            preferAbove = true
                        )
                    } else {
                        val startLine = layout.getLineForOffset(normalizedStart)
                        val endLine = layout.getLineForOffset((normalizedEndExclusive - 1).coerceAtLeast(0))
                        val selectionTop = layout.getLineTop(minOf(startLine, endLine))
                        val selectionBottom = layout.getLineBottom(maxOf(startLine, endLine))

                        calculateSelectionToolbarPosition(
                            anchorPosition = selectionState.anchorPosition,
                            popupSize = selectionPopupSize,
                            containerSize = containerSize,
                            selectionTop = selectionTop,
                            selectionBottom = selectionBottom
                        )
                    }
                }

                Popup(
                    alignment = androidx.compose.ui.Alignment.TopStart,
                    offset = IntOffset(
                        x = toolbarPosition.x.roundToInt(),
                        y = toolbarPosition.y.roundToInt()
                    ),
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = false
                    ),
                    onDismissRequest = { selectionState = TextSelectionState() }
                ) {
                    Box(
                        modifier = Modifier.onSizeChanged { selectionPopupSize = it }
                    ) {
                        SelectionToolbar(
                            selectedText = selectionState.selectedText,
                            onCopy = {
                                if (selectionState.selectedText.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(selectionState.selectedText))
                                }
                                selectionState = TextSelectionState()
                            },
                            onOpenExternal = {
                                if (selectionState.selectedText.isNotBlank()) {
                                    val opened = TextIntentLauncher.openSelectedText(selectionState.selectedText)
                                    if (!opened) {
                                        lastExternalOpenText = selectionState.selectedText
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "No compatible app found",
                                                actionLabel = "Copy",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed && lastExternalOpenText.isNotBlank()) {
                                                clipboardManager.setText(AnnotatedString(lastExternalOpenText))
                                            }
                                        }
                                        return@SelectionToolbar
                                    }
                                }
                                selectionState = TextSelectionState()
                            },
                            onHighlight = { color ->
                                val range = selectionState.selectionRange
                                onHighlightAdd(range.first, range.last + 1, selectionState.selectedText, color)
                                selectionState = TextSelectionState()
                            },
                        )
                    }
                }
            }

            layoutResult?.let { layout ->
                val startHandleOffset = layout.getBoundingBox(normalizedStart)
                val endHandleOffset = layout.getBoundingBox((normalizedEndExclusive - 1).coerceAtLeast(0))

                SelectionHandle(
                    x = startHandleOffset.left,
                    y = startHandleOffset.bottom,
                    containerSize = containerSize,
                    dragHysteresisPx = handleDragHysteresisPx,
                    onDragStart = {
                        selectionState = selectionState.copy(isDragging = true)
                    },
                    onDrag = { position ->
                        val handleOffset = snapOffsetToWordBoundary(
                            text = text.text,
                            movedOffset = layout.getOffsetForPosition(position).coerceIn(0, text.length),
                            fixedOffset = selectionState.endOffset,
                            layoutResult = layout,
                            previousOffset = selectionState.startOffset
                        )
                        selectionState = updateSelectionFromHandleOffset(
                            currentState = selectionState,
                            movingStartHandle = true,
                            newHandleOffset = handleOffset,
                            textContent = text.text,
                            layoutResult = layout,
                            textLength = text.length,
                            fallbackAnchor = position
                        )
                    },
                    onDragEnd = {
                        selectionState = finishSelectionDrag(selectionState)
                    }
                )

                SelectionHandle(
                    x = endHandleOffset.right,
                    y = endHandleOffset.bottom,
                    containerSize = containerSize,
                    dragHysteresisPx = handleDragHysteresisPx,
                    onDragStart = {
                        selectionState = selectionState.copy(isDragging = true)
                    },
                    onDrag = { position ->
                        val handleOffset = snapOffsetToWordBoundary(
                            text = text.text,
                            movedOffset = layout.getOffsetForPosition(position).coerceIn(0, text.length),
                            fixedOffset = selectionState.startOffset,
                            layoutResult = layout,
                            previousOffset = selectionState.endOffset
                        )
                        selectionState = updateSelectionFromHandleOffset(
                            currentState = selectionState,
                            movingStartHandle = false,
                            newHandleOffset = handleOffset,
                            textContent = text.text,
                            layoutResult = layout,
                            textLength = text.length,
                            fallbackAnchor = position
                        )
                    },
                    onDragEnd = {
                        selectionState = finishSelectionDrag(selectionState)
                    }
                )
            }
        }

        editingHighlight?.let { highlight ->
            val popupPosition = remember(editPopupAnchor, containerSize, editPopupSize) {
                calculatePopupPosition(
                    anchorPosition = editPopupAnchor,
                    popupSize = editPopupSize,
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
                onDismissRequest = { editingHighlight = null }
            ) {
                Box(
                    modifier = Modifier.onSizeChanged { editPopupSize = it }
                ) {
                    HighlightEditPopup(
                        highlight = highlight,
                        onColorChange = { color ->
                            onHighlightColorChange(highlight.id, color)
                            editingHighlight = null
                        },
                        onDelete = {
                            onHighlightRemove(highlight.id)
                            editingHighlight = null
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
