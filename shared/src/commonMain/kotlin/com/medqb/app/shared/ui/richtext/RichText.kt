package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.models.HighlightColor
import com.medqb.app.shared.data.models.HighlightSection
import com.medqb.app.shared.data.models.TextHighlight
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.ui.richtext.parser.RichTextParser

@Stable
internal class RichTextSelectionCoordinator {
    private var activeOwner: Any? = null
    private var dismissActive: (() -> Unit)? = null

    fun claim(owner: Any, onDismiss: () -> Unit) {
        if (activeOwner !== owner) {
            dismissActive?.invoke()
        }
        activeOwner = owner
        dismissActive = onDismiss
    }

    fun release(owner: Any) {
        if (activeOwner === owner) {
            activeOwner = null
            dismissActive = null
        }
    }

    fun dismissOthers(caller: Any) {
        if (activeOwner != null && activeOwner !== caller) {
            dismissActive?.invoke()
            activeOwner = null
            dismissActive = null
        }
    }

    fun dismissAll() {
        dismissActive?.invoke()
        activeOwner = null
        dismissActive = null
    }
}

internal val LocalRichTextSelectionCoordinator = compositionLocalOf<RichTextSelectionCoordinator?> { null }

@Immutable
data class RichTextHighlightConfig(
    val section: HighlightSection,
    val highlights: List<TextHighlight>,
    val onHighlightAdd: (HighlightSection, blockKey: String, startOffset: Int, endOffset: Int, highlightedText: String, color: HighlightColor) -> Unit,
    val onHighlightRemove: (highlightId: Long) -> Unit,
    val onHighlightColorChange: (highlightId: Long, color: HighlightColor) -> Unit,
    val onShowSnackbar: suspend (SnackbarMessage) -> Unit = {},
)

/**
 * Renders HTML content as styled Compose UI elements, with optional text highlighting support.
 */
@Composable
fun RichText(
    html: String,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null,
    palette: RichTextPalette = rememberRichTextPalette(),
    highlightConfig: RichTextHighlightConfig? = null,
) {
    val blocks = rememberRichTextBlocks(html, palette, showSelectedHighlight)
    RichTextInternal(
        blocks = blocks,
        tooltipResetKey = html,
        modifier = modifier,
        showSelectedHighlight = showSelectedHighlight,
        onLinkClick = onLinkClick,
        onMediaClick = onMediaClick,
        onTooltipClick = onTooltipClick,
        highlightConfig = highlightConfig,
    )
}

@Composable
fun RichText(
    blocks: List<RichTextBlock>,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    RichTextInternal(
        blocks = blocks,
        tooltipResetKey = blocks,
        modifier = modifier,
        showSelectedHighlight = showSelectedHighlight,
        onLinkClick = onLinkClick,
        onMediaClick = onMediaClick,
        onTooltipClick = onTooltipClick,
        highlightConfig = highlightConfig,
    )
}

@Composable
private fun RichTextInternal(
    blocks: List<RichTextBlock>,
    tooltipResetKey: Any?,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null,
    highlightConfig: RichTextHighlightConfig? = null,
) {
    if (blocks.isEmpty()) return
    val sourceTag = if (highlightConfig != null) "HighlightableRichText" else "RichText"
    val resolvedLinkHandler = rememberResolvedLinkHandler(onLinkClick, sourceTag = sourceTag)
    val resolvedMediaHandler = rememberResolvedMediaHandler(onMediaClick)
    val tooltipSupport = rememberRichTextTooltipSupport(
        resetKey = tooltipResetKey,
        onTooltipClick = onTooltipClick
    )
    val parentCoordinator = LocalRichTextSelectionCoordinator.current
    val selectionCoordinator = remember(parentCoordinator) {
        parentCoordinator ?: RichTextSelectionCoordinator()
    }

    val containerModifier = if (highlightConfig != null) {
        modifier.dismissSelectionOnTap(selectionCoordinator)
    } else {
        modifier
    }

    CompositionLocalProvider(LocalRichTextSelectionCoordinator provides selectionCoordinator) {
        Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            blocks.forEachIndexed { blockIndex, block ->
                RichTextBlockRenderer(
                    block = block,
                    blockIndex = blockIndex,
                    showSelectedHighlight = showSelectedHighlight,
                    onLinkClick = resolvedLinkHandler,
                    onMediaClick = resolvedMediaHandler,
                    onTooltipClick = tooltipSupport.onTooltipClick,
                    highlightConfig = highlightConfig,
                )
            }
        }
    }

    RichTextTooltipBottomSheet(
        content = tooltipSupport.tooltipContent,
        onDismissRequest = tooltipSupport.dismissTooltip
    )
}

@Composable
private fun rememberRichTextBlocks(
    rawHtml: String,
    palette: RichTextPalette,
    showSelectedHighlight: Boolean
): List<RichTextBlock> {
    return remember(rawHtml, palette, showSelectedHighlight) {
        val sanitizedHtml = rawHtml.trim()
        if (sanitizedHtml.isEmpty()) emptyList()
        else RichTextParser.parse(sanitizedHtml, palette, showSelectedHighlight)
    }
}

@Composable
internal fun rememberRichTextPalette(
    dictionaryText: Color = MaterialTheme.colorScheme.primary,
): RichTextPalette {
    val colors = MaterialTheme.colorScheme
    return remember(colors, dictionaryText) {
        RichTextPalette(
            importantBackground = colors.tertiaryContainer,
            importantText = colors.onTertiaryContainer,
            selectedBackground = colors.primaryContainer,
            selectedText = colors.onPrimaryContainer,
            linkText = colors.primary,
            dictionaryText = dictionaryText,
            abstractText = colors.onSurfaceVariant
        )
    }
}

internal fun Modifier.dismissSelectionOnTap(
    selectionCoordinator: RichTextSelectionCoordinator,
): Modifier = pointerInput(selectionCoordinator) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val up = waitForUpOrCancellation()
        if (up != null && !up.isConsumed) {
            selectionCoordinator.dismissAll()
        }
    }
}
