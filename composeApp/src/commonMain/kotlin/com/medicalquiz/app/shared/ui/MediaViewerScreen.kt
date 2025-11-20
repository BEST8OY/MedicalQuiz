package com.medicalquiz.app.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.platform.StorageProvider
import com.medicalquiz.app.shared.ui.richtext.RichText

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.utils.HtmlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaFiles: List<String>,
    startIndex: Int,
    mediaDescriptions: Map<String, MediaDescription>,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { mediaFiles.size })
    var currentIndex by remember { mutableStateOf(startIndex) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Media Viewer",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentIndex + 1} / ${mediaFiles.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                currentIndex = page
                val file = mediaFiles[page]
                MediaContent(
                    fileName = file,
                    description = mediaDescriptions[file]
                )
            }
        }
    }
}

@Composable
private fun MediaContent(
    fileName: String,
    description: MediaDescription?
) {
    val mediaType = getMediaType(fileName)
    
    when (mediaType) {
        MediaType.IMAGE -> ImageContent(fileName, description)
        MediaType.HTML -> HtmlContent(fileName)
        else -> UnsupportedContent(fileName, mediaType)
    }
}

@Composable
private fun HtmlContent(fileName: String) {
    val storageDir = StorageProvider.getAppStorageDirectory()
    val filePath = "$storageDir/media/$fileName"
    val htmlContent = remember(fileName) {
        FileSystemHelper.readText(filePath)?.let { 
            HtmlUtils.sanitizeForWebView(it) 
        }
    }

    if (htmlContent == null) {
        UnsupportedContent(fileName, MediaType.HTML)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            RichText(
                html = htmlContent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ImageContent(
    fileName: String,
    description: MediaDescription?
) {
    var showExplanation by remember { mutableStateOf(false) }
    val storageDir = StorageProvider.getAppStorageDirectory()
    val filePath = "$storageDir/media/$fileName"

    // Zoom state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Overlay state
    val overlayName = if (fileName.startsWith("big_", ignoreCase = true)) {
        fileName.substringBeforeLast('.') + ".svg"
    } else null
    
    val overlayPath = remember(overlayName) {
        overlayName?.let { name ->
            val path = "$storageDir/media/$name"
            if (FileSystemHelper.exists(path)) path else null
        }
    }
    var showOverlay by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        
                        if (scale > 1f || zoom != 1f) {
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale == 1f) offset = Offset.Zero
                            else {
                                val newOffset = offset + pan
                                // Optional: Add bounds checking here to prevent panning too far
                                offset = newOffset
                            }
                            
                            event.changes.forEach { 
                                if (it.positionChanged()) {
                                    it.consume() 
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            AsyncImage(
                model = filePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            if (overlayPath != null && showOverlay) {
                AsyncImage(
                    model = overlayPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        if (description != null) {
            IconButton(
                onClick = { showExplanation = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Show explanation",
                    tint = Color.White
                )
            }
        }

        if (overlayPath != null) {
            IconButton(
                onClick = { showOverlay = !showOverlay },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (showOverlay) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle overlay",
                    tint = Color.White
                )
            }
        }
    }

    if (showExplanation && description != null) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) {
                    Text("Close")
                }
            },
            title = {
                Text(description.title.ifBlank { "Explanation" })
            },
            text = {
                RichText(
                    html = description.description,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@Composable
private fun UnsupportedContent(fileName: String, type: MediaType) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Unsupported media type: $type\n$fileName",
            color = Color.White
        )
    }
}

private fun getMediaType(fileName: String): MediaType {
    return when (fileName.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> MediaType.IMAGE
        "mp4", "avi", "mkv", "mov", "webm", "3gp" -> MediaType.VIDEO
        "mp3", "wav", "ogg", "m4a", "aac", "flac" -> MediaType.AUDIO
        "html", "htm" -> MediaType.HTML
        else -> MediaType.UNKNOWN
    }
}
