package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.data.models.HighlightColor
import com.medicalquiz.app.shared.data.models.TextHighlight
import com.medicalquiz.app.shared.ui.LocalFontSize
import kotlin.math.roundToInt

/**
 * State for text selection within SelectableRichText.
 */
data class TextSelectionState(
    val isSelecting: Boolean = false,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val selectedText: String = ""
) {
    val hasSelection: Boolean get() = isSelecting && startOffset != endOffset
    val selectionRange: IntRange get() = minOf(startOffset, endOffset) until maxOf(startOffset, endOffset)
}

/**
 * State for highlight editing popup.
 */
data class HighlightEditState(
    val highlight: TextHighlight? = null,
    val position: Offset = Offset.Zero
)

/**
 * A wrapper around BasicText that supports text selection for highlighting.
 * 
 * Features:
 * - Long-press to start selection
 * - Drag to extend selection
 * - Floating toolbar for highlight actions
 * - Tap existing highlights to edit/delete
 */
@Composable
fun SelectableHighlightText(
    text: AnnotatedString,
    highlights: List<TextHighlight>,
    modifier: Modifier = Modifier,
    onHighlightAdd: (startOffset: Int, endOffset: Int, text: String, color: HighlightColor) -> Unit,
    onHighlightRemove: (highlightId: Long) -> Unit,
    onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null
) {
    var selectionState by remember { mutableStateOf(TextSelectionState()) }
    var editState by remember { mutableStateOf(HighlightEditState()) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    
    // Build annotated string with highlight backgrounds applied
    val highlightedText = remember(text, highlights) {
        applyHighlightsToText(text, highlights)
    }
    
    // Also apply selection background if selecting
    val displayText = remember(highlightedText, selectionState) {
        if (selectionState.hasSelection) {
            applySelectionToText(highlightedText, selectionState.selectionRange)
        } else {
            highlightedText
        }
    }
    
    Box(modifier = modifier) {
        BasicText(
            text = displayText,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(text, highlights) {
                    detectTapGestures(
                        onLongPress = { pos ->
                            // Start selection mode
                            layoutResult?.let { layout ->
                                val offset = layout.getOffsetForPosition(pos)
                                // Expand to word boundaries
                                val (start, end) = expandToWordBoundaries(text.text, offset)
                                selectionState = TextSelectionState(
                                    isSelecting = true,
                                    startOffset = start,
                                    endOffset = end,
                                    selectedText = text.text.substring(start, end)
                                )
                            }
                        },
                        onTap = { pos ->
                            layoutResult?.let { layout ->
                                val offset = layout.getOffsetForPosition(pos)
                                
                                // Check if tapped on existing highlight
                                val tappedHighlight = highlights.find { it.contains(offset) }
                                if (tappedHighlight != null) {
                                    // Show edit popup
                                    editState = HighlightEditState(
                                        highlight = tappedHighlight,
                                        position = pos
                                    )
                                    return@detectTapGestures
                                }
                                
                                // Check for link/tooltip annotations
                                text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                    onLinkClick?.invoke(it.item)
                                    return@detectTapGestures
                                }
                                text.getStringAnnotations("TOOLTIP", offset, offset).firstOrNull()?.let {
                                    onTooltipClick?.invoke(it.item)
                                    return@detectTapGestures
                                }
                                
                                // Clear selection if tapped elsewhere
                                if (selectionState.isSelecting) {
                                    selectionState = TextSelectionState()
                                }
                                if (editState.highlight != null) {
                                    editState = HighlightEditState()
                                }
                            }
                        }
                    )
                }
                .pointerInput(selectionState.isSelecting) {
                    if (selectionState.isSelecting) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                layoutResult?.let { layout ->
                                    val offset = layout.getOffsetForPosition(change.position)
                                    val newEnd = offset.coerceIn(0, text.length)
                                    val start = selectionState.startOffset
                                    val actualStart = minOf(start, newEnd)
                                    val actualEnd = maxOf(start, newEnd)
                                    selectionState = selectionState.copy(
                                        endOffset = newEnd,
                                        selectedText = text.text.substring(actualStart, actualEnd)
                                    )
                                }
                            }
                        )
                    }
                },
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = LocalFontSize.current * 1.375f,
                fontSize = LocalFontSize.current
            ),
            onTextLayout = { layoutResult = it }
        )
        
        // Selection toolbar
        AnimatedVisibility(
            visible = selectionState.hasSelection,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SelectionToolbar(
                position = calculateToolbarPosition(layoutResult, selectionState),
                onHighlight = { color ->
                    val range = selectionState.selectionRange
                    onHighlightAdd(range.first, range.last + 1, selectionState.selectedText, color)
                    selectionState = TextSelectionState()
                },
                onCancel = {
                    selectionState = TextSelectionState()
                }
            )
        }
        
        // Highlight edit popup
        editState.highlight?.let { highlight ->
            HighlightEditPopup(
                highlight = highlight,
                position = editState.position,
                onColorChange = { color ->
                    onHighlightColorChange(highlight.id, color)
                    editState = HighlightEditState()
                },
                onDelete = {
                    onHighlightRemove(highlight.id)
                    editState = HighlightEditState()
                },
                onDismiss = {
                    editState = HighlightEditState()
                }
            )
        }
    }
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
        
        // Copy existing span styles
        text.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        
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
                // Add annotation for tap detection
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
    selectionRange: IntRange
): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        text.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        
        val start = selectionRange.first.coerceIn(0, text.length)
        val end = (selectionRange.last + 1).coerceIn(start, text.length)
        if (start < end) {
            addStyle(
                SpanStyle(background = Color.Blue.copy(alpha = 0.3f)),
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
    
    // Find start of word
    var start = safeOffset
    while (start > 0 && !text[start - 1].isWhitespace()) {
        start--
    }
    
    // Find end of word
    var end = safeOffset
    while (end < text.length && !text[end].isWhitespace()) {
        end++
    }
    
    return start to end
}

/**
 * Calculate toolbar position based on selection.
 */
private fun calculateToolbarPosition(
    layoutResult: TextLayoutResult?,
    selectionState: TextSelectionState
): Offset {
    if (layoutResult == null || !selectionState.hasSelection) return Offset.Zero
    
    val range = selectionState.selectionRange
    val startRect = layoutResult.getBoundingBox(range.first.coerceIn(0, layoutResult.layoutInput.text.length - 1))
    
    return Offset(
        x = startRect.left,
        y = startRect.top - 60f // Above the text
    )
}

/**
 * Floating toolbar for highlight color selection.
 */
@Composable
private fun SelectionToolbar(
    position: Offset,
    onHighlight: (HighlightColor) -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = position.x.roundToInt().coerceAtLeast(0),
                    y = position.y.roundToInt().coerceAtLeast(0)
                )
            }
            .widthIn(max = 300.dp),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color options - show all colors
            HighlightColor.entries.forEach { color ->
                ColorButton(
                    color = color,
                    onClick = { onHighlight(color) }
                )
            }
            
            // Cancel button
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Popup for editing an existing highlight.
 */
@Composable
private fun HighlightEditPopup(
    highlight: TextHighlight,
    position: Offset,
    onColorChange: (HighlightColor) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = position.x.roundToInt().coerceAtLeast(0),
                    y = (position.y - 70f).roundToInt().coerceAtLeast(0)
                )
            }
            .widthIn(max = 320.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Edit Highlight",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Color options
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HighlightColor.entries.forEach { color ->
                    ColorButton(
                        color = color,
                        isSelected = color == highlight.color,
                        onClick = { onColorChange(color) }
                    )
                }
            }
            
            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onDelete,
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Done",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Circular color button for highlight color selection.
 */
@Composable
private fun ColorButton(
    color: HighlightColor,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color.toComposeColor())
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                }
            )
            .clickable(onClick = onClick)
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
