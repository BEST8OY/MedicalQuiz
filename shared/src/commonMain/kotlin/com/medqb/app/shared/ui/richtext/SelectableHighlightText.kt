package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.platform.PlatformKind
import com.medqb.app.shared.platform.getPlatformKind
import com.medqb.app.shared.ui.richtext.interaction.TextSelectionState
import com.medqb.app.shared.ui.richtext.interaction.selectableHighlightGestures
import com.medqb.app.shared.ui.richtext.styling.applyHighlightsToText
import com.medqb.app.shared.ui.richtext.styling.applySelectionToText

private val ANDROID_LONG_PRESS_DRAG_HYSTERESIS = 7.dp
private val DESKTOP_LONG_PRESS_DRAG_HYSTERESIS = 5.dp

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
    val hostState = checkNotNull(LocalRichTextHighlightHostState.current) {
        "SelectableHighlightText must be rendered inside a RichTextHighlightHost"
    }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val block = remember(text) { HighlightBlockRegistration(text = text) }

    SideEffect {
        block.onHighlightAdd = onHighlightAdd
        block.onHighlightRemove = onHighlightRemove
        block.onHighlightColorChange = onHighlightColorChange
        block.onLinkClick = onLinkClick
        block.onTooltipClick = onTooltipClick
        block.onShowSnackbar = onShowSnackbar
    }

    DisposableEffect(hostState, block) {
        onDispose {
            hostState.clearIfActive(block)
        }
    }

    // Reconcile active Edit UI if the backing highlights list updates (e.g., deleted or recolored)
    LaunchedEffect(highlights, hostState, block) {
        val currentEditing = hostState.editingFor(block) ?: return@LaunchedEffect
        val updated = highlights.firstOrNull { it.id == currentEditing.highlight.id }
        if (updated == null) {
            hostState.clearIfActive(block)
        } else if (updated != currentEditing.highlight) {
            hostState.updateEditingHighlight(block, updated)
        }
    }

    val activeSelection by remember(hostState, block) {
        derivedStateOf { hostState.selectionFor(block) }
    }

    val platformKind = remember { getPlatformKind() }
    val longPressDragHysteresis = remember(platformKind) {
        when (platformKind) {
            PlatformKind.Android -> ANDROID_LONG_PRESS_DRAG_HYSTERESIS
            PlatformKind.Desktop -> DESKTOP_LONG_PRESS_DRAG_HYSTERESIS
        }
    }
    val density = LocalDensity.current
    val longPressDragHysteresisPx = remember(density, longPressDragHysteresis) {
        with(density) { longPressDragHysteresis.toPx() }
    }

    val highlightedText = remember(text, highlights, isDarkTheme) {
        applyHighlightsToText(text, highlights, isDarkTheme = isDarkTheme)
    }
    val selectionColor = MaterialTheme.colorScheme.primaryContainer
    val displayText = remember(highlightedText, activeSelection, selectionColor) {
        val selection = activeSelection
        if (selection != null && selection.hasSelectionRange) {
            applySelectionToText(highlightedText, selection.selectionRange, selectionColor)
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

    BasicText(
        text = displayText,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                block.textCoordinates = coords
                val pos = coords.localToRoot(Offset.Zero)
                if (block.positionInRoot != pos) {
                    block.positionInRoot = pos
                }
            }
            .then(
                if (activeSelection?.isDragging == true) {
                    Modifier.platformSelectionMagnifier(
                        sourceCenter = {
                            val pos = block.dragPosition
                            val layout = block.layoutResult
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
                currentLayoutResult = { block.layoutResult },
                currentSelectionState = {
                    hostState.selectionFor(block)?.let { sel ->
                        TextSelectionState(
                            isSelecting = true,
                            isDragging = sel.isDragging,
                            startOffset = sel.startOffset,
                            endOffset = sel.endOffset,
                            selectedText = sel.selectedText,
                            isMouseSelection = sel.isMouseSelection,
                        )
                    } ?: TextSelectionState()
                },
                setSelectionState = { hostState.updateSelection(block, it) },
                setEditingHighlight = { hit ->
                    if (hit != null) {
                        hostState.showEditToolbar(block, hit)
                    } else if (hostState.editingFor(block) != null) {
                        hostState.clearIfActive(block)
                    }
                },
                setDragPosition = { block.dragPosition = it },
                onDismissAll = { hostState.dismiss() },
                onLinkClick = onLinkClick,
                onTooltipClick = onTooltipClick,
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
        onTextLayout = { block.layoutResult = it },
    )
}
