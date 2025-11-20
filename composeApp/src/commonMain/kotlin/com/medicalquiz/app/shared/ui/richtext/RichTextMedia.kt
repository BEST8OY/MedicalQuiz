package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.medicalquiz.app.shared.utils.HtmlUtils

@Composable
internal fun RichMedia(block: RichTextBlock.Media, onMediaClick: (String) -> Unit) {
    val mediaModel = remember(block.source, block.mediaRef) {
        mediaModelForSource(block.source, block.mediaRef)
    }
    if (mediaModel == null) return
    val clickTarget = block.mediaRef ?: block.source
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = when (block.alignment) {
            TextAlign.End -> Alignment.End
            TextAlign.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        AsyncImage(
            model = mediaModel,
            contentDescription = block.description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMediaClick(clickTarget) }
        )
        block.description?.let {
            androidx.compose.material3.Text(
                text = it,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = block.alignment
            )
        }
    }
}

internal fun mediaModelForSource(source: String, mediaRef: String?): Any? {
    val filename = mediaRef ?: extractMediaRef(source)
    if (filename != null) {
        HtmlUtils.getMediaPath(filename)?.let { path ->
            // Coil 3 supports file paths as strings
            return path
        }
    }
    if (source.startsWith("file://")) {
        return source.removePrefix("file://")
    }
    return source
}

internal fun extractMediaRef(source: String): String? {
    return source.substringAfterLast('/', "").takeIf { it.isNotBlank() }
}
