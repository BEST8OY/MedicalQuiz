package com.medqb.app.shared.ui.richtext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.platform.PlatformKind
import com.medqb.app.shared.platform.TextIntentLauncher
import com.medqb.app.shared.platform.getPlatformKind
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.domain.SnackbarMessage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ANDROID_LONG_PRESS_DRAG_HYSTERESIS = 7.dp
private val DESKTOP_LONG_PRESS_DRAG_HYSTERESIS = 5.dp

private class SelectableHighlightTextState {
    var selectionState by mutableStateOf(TextSelectionState())
    var editingHighlight by mutableStateOf<TextHighlight?>(null)
    var editPopupAnchor by mutableStateOf(Offset.Zero)
    var layoutResult by mutableStateOf<TextLayoutResult?>(null)
    var containerSize by mutableStateOf(IntSize.Zero)
    var selectionPopupSize by mutableStateOf(IntSize.Zero)
    var editPopupSize by mutableStateOf(IntSize.Zero)
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
    val state = remember { SelectableHighlightTextState() }

    val platformKind = remember { getPlatformKind() }
    val longPressDragHysteresis = remember(platformKind) {
        when (platformKind) {
            PlatformKind.Android -> ANDROID_LONG_PRESS_DRAG_HYSTERESIS
            PlatformKind.Desktop -> DESKTOP_LONG_PRESS_DRAG_HYSTERESIS
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val longPressDragHysteresisPx = remember(density) {
        with(density) { longPressDragHysteresis.toPx() }
    }

    val highlightedText = remember(text, highlights) {
        applyHighlightsToText(text, highlights)
    }
    val selectionColor = MaterialTheme.colorScheme.primaryContainer
    val displayText = remember(highlightedText, state.selectionState, selectionColor) {
        if (state.selectionState.isSelecting) {
            applySelectionToText(highlightedText, state.selectionState.selectionRange, selectionColor)
        } else {
            highlightedText
        }
    }

    Box(
        modifier = modifier.onSizeChanged { state.containerSize = it }
    ) {
        BasicText(
            text = displayText,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (state.selectionState.isDragging) {
                        Modifier.platformSelectionMagnifier(
                            sourceCenter = {
                                val pos = state.dragPosition
                                val layout = state.layoutResult
                                if (layout != null && text.isNotEmpty()) {
                                    val line = layout.getLineForVerticalPosition(pos.y)
                                    val lineCenterY = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f

                                    // Clamp X to line's visual bounds to prevent zooming empty space
                                    val lo = layout.getLineLeft(line)
                                    val hi = layout.getLineRight(line)
                                    val clampedX = if (hi > lo) pos.x.coerceIn(lo, hi) else pos.x

                                    Offset(x = clampedX, y = lineCenterY)
                                } else {
                                    pos
                                }
                            },
                            magnifierCenter = null,
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
                    setEditPopupAnchor = { state.editPopupAnchor = it },
                    setDragPosition = { state.dragPosition = it },
                    onLinkClick = onLinkClick,
                    onTooltipClick = onTooltipClick
                ),
            style = textStyle.copy(
                color = if (color != Color.Unspecified) color else if (textStyle.color != Color.Unspecified) textStyle.color else LocalContentColor.current,
                lineHeight = (if (textStyle.fontSize == androidx.compose.ui.unit.TextUnit.Unspecified) {
                    MaterialTheme.typography.bodyMedium.fontSize
                } else {
                    textStyle.fontSize
                }) * LINE_HEIGHT_MULTIPLIER,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                ),
                fontSize = if (textStyle.fontSize == androidx.compose.ui.unit.TextUnit.Unspecified) {
                    MaterialTheme.typography.bodyMedium.fontSize
                } else {
                    textStyle.fontSize
                },
            ),
            onTextLayout = { state.layoutResult = it }
        )

        if (state.selectionState.hasSelectionRange) {
            val safeTextLength = text.length.coerceAtLeast(1)
            val normalizedStart = minOf(state.selectionState.startOffset, state.selectionState.endOffset)
                .coerceIn(0, safeTextLength - 1)
            val normalizedEndExclusive = maxOf(state.selectionState.startOffset, state.selectionState.endOffset)
                .coerceIn(normalizedStart + 1, safeTextLength)

            if (state.selectionState.showSelectionToolbar) {
                val toolbarPosition = remember(
                    state.selectionState.anchorPosition,
                    state.containerSize,
                    state.selectionPopupSize,
                    state.layoutResult,
                    normalizedStart,
                    normalizedEndExclusive
                ) {
                    val layout = state.layoutResult
                    if (layout == null) {
                        calculatePopupPosition(
                            anchorPosition = state.selectionState.anchorPosition,
                            popupSize = state.selectionPopupSize,
                            containerSize = state.containerSize,
                            preferAbove = true
                        )
                    } else {
                        val startLine = layout.getLineForOffset(normalizedStart)
                        val endLine = layout.getLineForOffset((normalizedEndExclusive - 1).coerceAtLeast(0))
                        val selectionTop = layout.getLineTop(minOf(startLine, endLine))
                        val selectionBottom = layout.getLineBottom(maxOf(startLine, endLine))

                        calculateSelectionToolbarPosition(
                            anchorPosition = state.selectionState.anchorPosition,
                            popupSize = state.selectionPopupSize,
                            containerSize = state.containerSize,
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
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    ),
                    onDismissRequest = { state.selectionState = TextSelectionState() }
                ) {
                    val motionScheme = MaterialTheme.motionScheme
                    // Enter animation only — exit is instant because Popup removal
                    // tears down composition before AnimatedVisibility can animate out.
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                                slideInVertically(motionScheme.defaultSpatialSpec()) { -it / 4 }
                    ) {
                        Box(
                            modifier = Modifier.onSizeChanged { state.selectionPopupSize = it }
                        ) {
                            SelectionToolbar(
                                selectedText = state.selectionState.selectedText,
                                onCopy = {
                                    val copiedText = state.selectionState.selectedText
                                    if (copiedText.isNotBlank()) {
                                        clipboard.setPlainText(AnnotatedString(copiedText))
                                    }
                                    state.selectionState = TextSelectionState()
                                },
                                onOpenExternal = {
                                    if (state.selectionState.selectedText.isNotBlank()) {
                                        val opened = TextIntentLauncher.openSelectedText(state.selectionState.selectedText)
                                        if (!opened) {
                                            val failedText = state.selectionState.selectedText
                                            coroutineScope.launch {
                                                onShowSnackbar(
                                                    SnackbarMessage.Action(
                                                        message = "No compatible app found",
                                                        actionLabel = "Copy",
                                                        onActionPerformed = {
                                                            if (failedText.isNotBlank()) {
                                                                clipboard.setPlainText(AnnotatedString(failedText))
                                                            }
                                                        },
                                                    )
                                                )
                                            }
                                            return@SelectionToolbar
                                        }
                                    }
                                    state.selectionState = TextSelectionState()
                                },
                                onHighlight = { color ->
                                    val range = state.selectionState.selectionRange
                                    onHighlightAdd(range.first, range.last + 1, state.selectionState.selectedText, color)
                                    state.selectionState = TextSelectionState()
                                },
                            )
                        }
                    }
                }
            }
        }

        state.editingHighlight?.let { highlight ->
            val popupPosition = remember(state.editPopupAnchor, state.containerSize, state.editPopupSize) {
                calculatePopupPosition(
                    anchorPosition = state.editPopupAnchor,
                    popupSize = state.editPopupSize,
                    containerSize = state.containerSize,
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
                onDismissRequest = { state.editingHighlight = null }
            ) {
                val motionScheme = MaterialTheme.motionScheme
                // Enter animation only — exit is instant because Popup removal
                // tears down composition before AnimatedVisibility can animate out.
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                            slideInVertically(motionScheme.defaultSpatialSpec()) { -it / 4 }
                ) {
                    Box(
                        modifier = Modifier.onSizeChanged { state.editPopupSize = it }
                    ) {
                        HighlightEditPopup(
                            highlight = highlight,
                            onColorChange = { color ->
                                onHighlightColorChange(highlight.id, color)
                                state.editingHighlight = null
                            },
                            onDelete = {
                                onHighlightRemove(highlight.id)
                                state.editingHighlight = null
                            }
                        )
                    }
                }
            }
        }
    }
}
