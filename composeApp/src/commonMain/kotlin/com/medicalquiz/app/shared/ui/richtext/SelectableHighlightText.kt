package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.medicalquiz.app.shared.data.models.HighlightColor
import com.medicalquiz.app.shared.data.models.TextHighlight
import com.medicalquiz.app.shared.platform.TextIntentLauncher
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * State for text selection within SelectableRichText.
 */
private data class TextSelectionState(
    val isSelecting: Boolean = false,
    val isDragging: Boolean = false, // True while actively dragging
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val selectedText: String = "",
    val anchorPosition: Offset = Offset.Zero // For popup positioning
) {
    val hasSelection: Boolean get() = isSelecting && startOffset != endOffset && !isDragging
    val selectionRange: IntRange get() = minOf(startOffset, endOffset) until maxOf(startOffset, endOffset)
}

/**
 * A wrapper around BasicText that supports text selection for highlighting.
 * 
 * Features:
 * - Long-press to start selection
 * - Drag to extend selection (toolbar hidden while dragging)
 * - Floating toolbar appears after selection complete
 * - Tap existing highlights to edit/delete
 */
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
    
    // Build annotated string with highlight backgrounds applied
    val highlightedText = remember(text, highlights) {
        applyHighlightsToText(text, highlights)
    }
    
    // Also apply selection background if selecting
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
                .pointerInput(text, highlights) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        
                        if (longPress != null) {
                            // Long press detected - start selection
                            layoutResult?.let { layout ->
                                val offset = layout.getOffsetForPosition(longPress.position)
                                val (start, end) = expandToWordBoundaries(text.text, offset)
                                selectionState = TextSelectionState(
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
                            }
                            
                            // Track drag to extend selection
                            do {
                                val event = awaitPointerEvent()
                                val position = event.changes.firstOrNull()?.position ?: break
                                
                                layoutResult?.let { layout ->
                                    val offset = layout.getOffsetForPosition(position)
                                    val newEnd = offset.coerceIn(0, text.length)
                                    val start = selectionState.startOffset
                                    val actualStart = minOf(start, newEnd)
                                    val actualEnd = maxOf(start, newEnd)
                                    
                                    if (actualEnd > actualStart) {
                                        selectionState = selectionState.copy(
                                            endOffset = newEnd,
                                            selectedText = text.text.substring(actualStart, actualEnd),
                                            anchorPosition = calculateRangeAnchor(
                                                layoutResult = layout,
                                                textLength = text.length,
                                                startOffset = actualStart,
                                                endOffset = actualEnd,
                                                fallbackAnchor = position
                                            )
                                        )
                                    }
                                }
                                
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            
                            // Drag ended - show toolbar
                            if (selectionState.startOffset != selectionState.endOffset) {
                                selectionState = selectionState.copy(isDragging = false)
                            } else {
                                selectionState = TextSelectionState()
                            }
                        } else {
                            // Regular tap
                            layoutResult?.let { layout ->
                                val offset = layout.getOffsetForPosition(down.position)
                                
                                // Check if tapped on existing highlight
                                val tappedHighlight = highlights.find { it.contains(offset) }
                                if (tappedHighlight != null) {
                                    editingHighlight = tappedHighlight
                                    editPopupAnchor = calculateRangeAnchor(
                                        layoutResult = layout,
                                        textLength = text.length,
                                        startOffset = tappedHighlight.startOffset,
                                        endOffset = tappedHighlight.endOffset,
                                        fallbackAnchor = down.position
                                    )
                                    selectionState = TextSelectionState()
                                    return@awaitEachGesture
                                }
                                
                                // Check for link/tooltip annotations
                                text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                    onLinkClick?.invoke(it.item)
                                    return@awaitEachGesture
                                }
                                text.getStringAnnotations("TOOLTIP", offset, offset).firstOrNull()?.let {
                                    onTooltipClick?.invoke(it.item)
                                    return@awaitEachGesture
                                }
                                
                                // Clear states
                                selectionState = TextSelectionState()
                                editingHighlight = null
                            }
                        }
                    }
                },
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
        
        // Selection toolbar - only visible when selection complete (not dragging)
        if (selectionState.hasSelection) {
            val toolbarPosition = remember(selectionState.anchorPosition, containerSize, selectionPopupSize) {
                calculatePopupPosition(
                    anchorPosition = selectionState.anchorPosition,
                    popupSize = selectionPopupSize,
                    containerSize = containerSize,
                    preferAbove = true
                )
            }
            
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    x = toolbarPosition.x.roundToInt(),
                    y = toolbarPosition.y.roundToInt()
                ),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
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

            val safeTextLength = text.length.coerceAtLeast(1)
            val normalizedStart = minOf(selectionState.startOffset, selectionState.endOffset)
                .coerceIn(0, safeTextLength - 1)
            val normalizedEndExclusive = maxOf(selectionState.startOffset, selectionState.endOffset)
                .coerceIn(normalizedStart + 1, safeTextLength)

            layoutResult?.let { layout ->
                val startHandleOffset = layout.getBoundingBox(normalizedStart)
                val endHandleOffset = layout.getBoundingBox((normalizedEndExclusive - 1).coerceAtLeast(0))

                SelectionHandle(
                    x = startHandleOffset.left,
                    y = startHandleOffset.bottom,
                    containerSize = containerSize,
                    onDrag = { position ->
                        val handleOffset = layout.getOffsetForPosition(position).coerceIn(0, text.length)
                        val existingEnd = selectionState.endOffset
                        val actualStart = minOf(handleOffset, existingEnd)
                        val actualEnd = maxOf(handleOffset, existingEnd)
                        if (actualEnd > actualStart) {
                            selectionState = selectionState.copy(
                                startOffset = handleOffset,
                                selectedText = text.text.substring(actualStart, actualEnd),
                                anchorPosition = calculateRangeAnchor(
                                    layoutResult = layout,
                                    textLength = text.length,
                                    startOffset = actualStart,
                                    endOffset = actualEnd,
                                    fallbackAnchor = position
                                )
                            )
                        }
                    }
                )

                SelectionHandle(
                    x = endHandleOffset.right,
                    y = endHandleOffset.bottom,
                    containerSize = containerSize,
                    onDrag = { position ->
                        val handleOffset = layout.getOffsetForPosition(position).coerceIn(0, text.length)
                        val existingStart = selectionState.startOffset
                        val actualStart = minOf(existingStart, handleOffset)
                        val actualEnd = maxOf(existingStart, handleOffset)
                        if (actualEnd > actualStart) {
                            selectionState = selectionState.copy(
                                endOffset = handleOffset,
                                selectedText = text.text.substring(actualStart, actualEnd),
                                anchorPosition = calculateRangeAnchor(
                                    layoutResult = layout,
                                    textLength = text.length,
                                    startOffset = actualStart,
                                    endOffset = actualEnd,
                                    fallbackAnchor = position
                                )
                            )
                        }
                    }
                )
            }
        }
        
        // Highlight edit popup
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
                alignment = Alignment.TopStart,
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

@Composable
private fun SelectionHandle(
    x: Float,
    y: Float,
    containerSize: IntSize,
    onDrag: (Offset) -> Unit
) {
    val handleRadius = 7.dp
    val handleDiameterPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        (handleRadius * 2).toPx()
    }

    val maxX = (containerSize.width - handleDiameterPx).coerceAtLeast(0f)
    val maxY = (containerSize.height - handleDiameterPx).coerceAtLeast(0f)

    val adjustedX = (x - handleDiameterPx / 2f).coerceIn(0f, maxX)
    val adjustedY = y.coerceIn(0f, maxY)

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = adjustedX
                translationY = adjustedY
            }
            .size(handleRadius * 2)
            .drawBehind {
                drawCircle(
                    color = MaterialTheme.colorScheme.primary,
                    radius = size.minDimension / 2f
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position ?: break
                        onDrag(
                            Offset(
                                x = adjustedX + pos.x,
                                y = adjustedY + pos.y
                            )
                        )
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    )
}

/**
 * Calculates a stable popup anchor based on selected text range.
 */
private fun calculateRangeAnchor(
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

/**
 * Calculate popup position that stays in bounds and auto-flips vertically.
 */
private fun calculatePopupPosition(
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

/**
 * Apply stored highlights as background spans to the text.
 */
private fun applyHighlightsToText(
    text: AnnotatedString,
    highlights: List<TextHighlight>
): AnnotatedString {
    if (highlights.isEmpty()) return text
    
    return buildAnnotatedString {
        append(text)
        
        // Add highlight backgrounds
        highlights.forEach { highlight ->
            val start = highlight.startOffset.coerceIn(0, text.length)
            val end = highlight.endOffset.coerceIn(start, text.length)
            if (start < end) {
                addStyle(
                    SpanStyle(background = highlight.color.toComposeColor().copy(alpha = 0.4f)),
                    start,
                    end
                )
                addStringAnnotation("HIGHLIGHT", highlight.id.toString(), start, end)
            }
        }
    }
}

/**
 * Apply selection background to text.
 */
private fun applySelectionToText(
    text: AnnotatedString,
    selectionRange: IntRange,
    selectionColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        
        val start = selectionRange.first.coerceIn(0, text.length)
        val end = (selectionRange.last + 1).coerceIn(start, text.length)
        if (start < end) {
            addStyle(
                SpanStyle(background = selectionColor),
                start,
                end
            )
        }
    }
}

/**
 * Expand a character offset to word boundaries.
 */
private fun expandToWordBoundaries(text: String, offset: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0
    
    val safeOffset = offset.coerceIn(0, text.lastIndex)
    
    var start = safeOffset
    while (start > 0 && !text[start - 1].isWhitespace()) {
        start--
    }
    
    var end = safeOffset
    while (end < text.length && !text[end].isWhitespace()) {
        end++
    }
    
    return start to end
}

/**
 * Floating toolbar for highlight color selection.
 * Uses modern Material 3 patterns with Popup for proper layering.
 */
@Composable
private fun SelectionToolbar(
    selectedText: String,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    onHighlight: (HighlightColor) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarActionButton(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy",
                    enabled = selectedText.isNotBlank(),
                    onClick = onCopy
                )
                ToolbarActionButton(
                    icon = Icons.Rounded.OpenInNew,
                    label = "Dictionary",
                    enabled = selectedText.isNotBlank(),
                    onClick = onOpenExternal
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HighlightColor.entries.forEach { color ->
                    HighlightColorChip(
                        color = color,
                        isSelected = false,
                        onClick = { onHighlight(color) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
        }
    }
}

/**
 * Popup for editing an existing highlight.
 */
@Composable
private fun HighlightEditPopup(
    highlight: TextHighlight,
    onColorChange: (HighlightColor) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HighlightColor.entries.forEach { color ->
                HighlightColorChip(
                    color = color,
                    isSelected = color == highlight.color,
                    onClick = { onColorChange(color) }
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete highlight",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Crisp circular color chip using Canvas drawing for sharp edges.
 */
@Composable
private fun HighlightColorChip(
    color: HighlightColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val composeColor = color.toComposeColor()
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isSelected) 2.5.dp else 1.dp
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer {
                // Ensure crisp rendering
                clip = true
                shape = CircleShape
            }
            .drawBehind {
                // Draw filled circle for crisp color
                drawCircle(color = composeColor)
            }
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // No ripple for cleaner look
                onClick = onClick
            )
    )
}

/**
 * Convert HighlightColor to Compose Color.
 */
internal fun HighlightColor.toComposeColor(): Color {
    val hex = this.hex.removePrefix("#")
    val colorLong = hex.toLongOrNull(16) ?: return Color.Yellow
    return Color(
        red = ((colorLong shr 16) and 0xFF) / 255f,
        green = ((colorLong shr 8) and 0xFF) / 255f,
        blue = (colorLong and 0xFF) / 255f
    )
}
