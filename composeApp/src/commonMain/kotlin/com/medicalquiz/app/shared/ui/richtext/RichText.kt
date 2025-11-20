package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.ui.richtext.parser.RichTextParser

@Composable
fun RichText(
    html: String,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null
) {
    val palette = rememberRichTextPalette()
    val blocks = rememberRichTextBlocks(html, palette, showSelectedHighlight)
    RichText(
        blocks = blocks,
        modifier = modifier,
        onLinkClick = onLinkClick,
        onMediaClick = onMediaClick,
        onTooltipClick = onTooltipClick
    )
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun RichText(
    blocks: List<RichTextBlock>,
    modifier: Modifier = Modifier,
    showSelectedHighlight: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
    onMediaClick: ((String) -> Unit)? = null,
    onTooltipClick: ((String) -> Unit)? = null
) {
    if (blocks.isEmpty()) return
    val resolvedLinkHandler = rememberLinkHandler(onLinkClick)
    val resolvedMediaHandler = rememberMediaHandler(onMediaClick)
    var tooltipMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(blocks) { tooltipMessage = null }
    val resolvedTooltipHandler = remember(onTooltipClick) {
        onTooltipClick ?: { message -> tooltipMessage = message }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        blocks.forEach { block ->
            RichTextBlockRenderer(
                block = block,
                onLinkClick = resolvedLinkHandler,
                onMediaClick = resolvedMediaHandler,
                onTooltipClick = resolvedTooltipHandler
            )
        }
    }
    tooltipMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { tooltipMessage = null },
            confirmButton = {
                TextButton(onClick = { tooltipMessage = null }) {
                    androidx.compose.material3.Text(text = "Close")
                }
            },
            text = {
                androidx.compose.material3.Text(text = message)
            },
            title = {
                androidx.compose.material3.Text(text = "Description")
            }
        )
    }
}

@Composable
private fun rememberRichTextBlocks(
    rawHtml: String,
    palette: RichTextPalette,
    showSelectedHighlight: Boolean
): List<RichTextBlock> {
    val sanitizedHtml = remember(rawHtml) { rawHtml.trim() }
    return remember(sanitizedHtml, palette, showSelectedHighlight) {
        if (sanitizedHtml.isEmpty()) emptyList()
        else RichTextParser.parse(sanitizedHtml, palette, showSelectedHighlight)
    }
}

@Composable
private fun rememberLinkHandler(onLinkClick: ((String) -> Unit)?): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(onLinkClick, uriHandler) {
        onLinkClick ?: { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: Throwable) {
                // Ignore failures opening deep links.
            }
        }
    }
}

@Composable
private fun rememberMediaHandler(onMediaClick: ((String) -> Unit)?): (String) -> Unit {
    return remember(onMediaClick) { onMediaClick ?: {} }
}

@Composable
internal fun rememberRichTextPalette(): RichTextPalette {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        RichTextPalette(
            importantBackground = colors.tertiaryContainer,
            importantText = colors.onTertiaryContainer,
            selectedBackground = colors.primaryContainer,
            selectedText = colors.onPrimaryContainer,
            dictionaryText = colors.primary,
            abstractText = colors.onSurfaceVariant
        )
    }
}
