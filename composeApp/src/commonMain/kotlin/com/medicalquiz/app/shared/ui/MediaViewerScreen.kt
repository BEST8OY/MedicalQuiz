package com.medicalquiz.app.shared.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.StorageProvider
import com.medicalquiz.app.shared.ui.richtext.RichText
import com.medicalquiz.app.shared.utils.HtmlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaFiles: List<String>,
    startIndex: Int,
    mediaDescriptions: Map<String, MediaDescription>,
    onBack: () -> Unit,
) {
    BackHandler(enabled = true, onBack = onBack)

    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { mediaFiles.size }
    )
    // Sync currentIndex with pagerState to avoid duplication
    val currentIndex = pagerState.currentPage
    var isZoomed by remember { mutableStateOf(false) }

    // Reset zoom when page changes
    LaunchedEffect(currentIndex) {
        isZoomed = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Media Viewer",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${currentIndex + 1} / ${mediaFiles.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed,
            ) { page ->
                currentIndex = page
                val file = mediaFiles[page]
                MediaContent(
                    fileName = file,
                    description = mediaDescriptions[file],
                    onZoomChanged = { isZoomed = it },
                )
            }
        }
    }
}

@Composable
private fun MediaContent(
    fileName: String,
    description: MediaDescription?,
    onZoomChanged: (Boolean) -> Unit,
) {
    val mediaType = remember(fileName) { getMediaType(fileName) }

    when (mediaType) {
        MediaType.IMAGE -> ImageContent(
            fileName = fileName,
            description = description,
            onZoomChanged = onZoomChanged,
        )
        MediaType.HTML -> HtmlContent(fileName = fileName)
        else -> UnsupportedContent(fileName = fileName, type = mediaType)
    }
}

@Composable
private fun HtmlContent(fileName: String) {
    val storageDir = StorageProvider.getAppStorageDirectory()
    val filePath = remember(fileName) { "$storageDir/media/$fileName" }

    val htmlContent = remember(filePath) {
        FileSystemHelper.readText(filePath)?.let {
            HtmlUtils.sanitizeForRichText(it)
        }
    }

    // Memoize scroll state to preserve scroll position across recompositions
    val scrollState = rememberScrollState()

    if (htmlContent.isNullOrBlank()) {
        UnsupportedContent(fileName = fileName, type = MediaType.HTML)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            RichText(
                html = htmlContent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ImageContent(
    fileName: String,
    description: MediaDescription?,
    onZoomChanged: (Boolean) -> Unit,
) {
    var showExplanation by remember { mutableStateOf(false) }
    val storageDir = StorageProvider.getAppStorageDirectory()
    val filePath = remember(fileName) { "$storageDir/media/$fileName" }

    val coroutineScope = rememberCoroutineScope()

    // Animated states
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Notify parent of zoom state
    LaunchedEffect(scale.value) {
        onZoomChanged(scale.value > 1f)
    }

    // Overlay state - memoize with both dependencies for stability
    val overlayName = remember(fileName) {
        if (fileName.startsWith("big_", ignoreCase = true)) {
            fileName.substringBeforeLast('.') + ".svg"
        } else null
    }

    val overlayPath = remember(overlayName, storageDir) {
        overlayName?.let { name ->
            val path = "$storageDir/media/$name"
            path.takeIf { FileSystemHelper.exists(it) }
        }
    }

    var showOverlay by remember { mutableStateOf(true) }

    // Boundaries (in px) - computed from container size
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidth = density.run { maxWidth.toPx() }
        val containerHeight = density.run { maxHeight.toPx() }

        fun clampOffsetForScale(offset: Offset, scaleVal: Float): Offset {
            // Allow panning only when scaled size > container
            val scaledWidth = containerWidth * scaleVal
            val scaledHeight = containerHeight * scaleVal
            val maxX = maxOf(0f, (scaledWidth - containerWidth) / 2f)
            val maxY = maxOf(0f, (scaledHeight - containerHeight) / 2f)
            val clampedX = offset.x.coerceIn(-maxX, maxX)
            val clampedY = offset.y.coerceIn(-maxY, maxY)
            return Offset(clampedX, clampedY)
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val newScale = (scale.value * zoomChange).coerceIn(1f, 5f)
            scale.value = newScale
            val newOffset = offset.value + panChange
            offset.value = clampOffsetForScale(newOffset, newScale)
        }

        // Double-tap zoom handling
        val doubleTapModifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { tapOffset ->
                coroutineScope.launch {
                    if (scale.value > 1f) {
                        // Reset to 1x with animation
                        scale.animateTo(1f)
                        offset.animateTo(Offset.Zero)
                    } else {
                        // Zoom in to 2.5x, center on tapped position
                        val targetScale = 2.5f
                        val centerX = containerWidth / 2f
                        val centerY = containerHeight / 2f
                        val centeredOffset = Offset(
                            x = (centerX - tapOffset.x) * (targetScale - 1f),
                            y = (centerY - tapOffset.y) * (targetScale - 1f),
                        )
                        scale.animateTo(targetScale)
                        offset.animateTo(clampOffsetForScale(centeredOffset, targetScale))
                    }
                }
            })
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(doubleTapModifier)
                .transformable(transformState)
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    translationX = offset.value.x,
                    translationY = offset.value.y,
                ),
        ) {
            AsyncImage(
                model = filePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            if (overlayPath != null && showOverlay) {
                AsyncImage(
                    model = overlayPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            if (description != null) {
                IconButton(
                    onClick = { showExplanation = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Show explanation",
                        tint = Color.White,
                    )
                }
            }

            if (overlayPath != null) {
                IconButton(
                    onClick = { showOverlay = !showOverlay },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                ) {
                    Icon(
                        imageVector = if (showOverlay) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Toggle overlay",
                        tint = Color.White,
                    )
                }
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
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
private fun UnsupportedContent(fileName: String, type: MediaType) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Unsupported media type: $type\n$fileName",
            color = Color.White,
        )
    }
}

private fun getMediaType(fileName: String): MediaType {
    val extension = fileName.substringAfterLast('.').lowercase()
    return when (extension) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> MediaType.IMAGE
        "mp4", "avi", "mkv", "mov", "webm", "3gp" -> MediaType.VIDEO
        "mp3", "wav", "ogg", "m4a", "aac", "flac" -> MediaType.AUDIO
        "html", "htm" -> MediaType.HTML
        else -> MediaType.UNKNOWN
    }
}
