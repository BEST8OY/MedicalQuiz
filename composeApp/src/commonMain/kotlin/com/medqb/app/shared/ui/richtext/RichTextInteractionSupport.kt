package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

internal data class RichTextTooltipSupport(
    val onTooltipClick: (RichTextTooltipContent) -> Unit,
    val tooltipContent: RichTextTooltipContent?,
    val dismissTooltip: () -> Unit
)

internal data class RichTextTooltipContent(
    val title: String,
    val message: String
)

@Composable
internal fun rememberResolvedLinkHandler(
    onLinkClick: ((String) -> Unit)?,
    sourceTag: String
): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(onLinkClick, uriHandler, sourceTag) {
        onLinkClick ?: { url ->
            try {
                uriHandler.openUri(url)
            } catch (e: Exception) {
                println("$sourceTag: Failed to open URL '$url': ${e.message}")
            } catch (e: Error) {
                println("$sourceTag: Critical error opening URL '$url': ${e.message}")
            }
        }
    }
}

@Composable
internal fun rememberResolvedMediaHandler(onMediaClick: ((String) -> Unit)?): (String) -> Unit {
    return remember(onMediaClick) { onMediaClick ?: {} }
}

@Composable
internal fun rememberRichTextTooltipSupport(
    resetKey: Any?,
    onTooltipClick: ((String) -> Unit)?
): RichTextTooltipSupport {
    var tooltipContent by remember { mutableStateOf<RichTextTooltipContent?>(null) }
    LaunchedEffect(resetKey) { tooltipContent = null }

    val resolvedTooltipHandler = remember(onTooltipClick) {
        onTooltipClick?.let { external ->
            { content: RichTextTooltipContent -> external(content.message) }
        } ?: { content: RichTextTooltipContent -> tooltipContent = content }
    }

    return remember(resolvedTooltipHandler, tooltipContent) {
        RichTextTooltipSupport(
            onTooltipClick = resolvedTooltipHandler,
            tooltipContent = tooltipContent,
            dismissTooltip = { tooltipContent = null }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RichTextTooltipBottomSheet(
    content: RichTextTooltipContent?,
    onDismissRequest: () -> Unit
) {
    content?.let {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            dragHandle = {
                BottomSheetDefaults.DragHandle()
            }
        ) {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                androidx.compose.material3.Text(
                    text = it.title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
                androidx.compose.material3.Text(text = it.message)
            }
        }
    }
}
