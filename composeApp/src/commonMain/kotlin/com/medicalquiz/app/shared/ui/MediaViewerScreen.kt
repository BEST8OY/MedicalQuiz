package com.medicalquiz.app.shared.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.StorageProvider
import com.medicalquiz.app.shared.ui.richtext.RichText
import com.medicalquiz.app.shared.utils.HtmlUtils
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// Animation and interaction constants
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val FADE_INTENSITY = 0.5f
private const val MIN_SCALE = 1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaFiles: List<String>,
    startIndex: Int,
    mediaDescriptions: Map<String, MediaDescription>,
    onBack: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onBack)

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
                beyondViewportPageCount = 1,
            ) { page ->
                val file = mediaFiles[page]
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Fade effect as page transitions
                            alpha = 1f - (pageOffset.coerceIn(-1f, 1f).absoluteValue * FADE_INTENSITY)
                        },
                ) {
                    MediaContent(
                        fileName = file,
                        description = mediaDescriptions[file],
                        onZoomChanged = { isZoomed = it },
                    )
                }
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

    val scope = rememberCoroutineScope()

    // Zoom state
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Notify parent of zoom state
    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f) // Small threshold to prevent jitter
    }

    // Overlay state
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidth = density.run { maxWidth.toPx() }
        val containerHeight = density.run { maxHeight.toPx() }

        // Clamp offset to keep image within bounds
        fun clampOffset(proposedOffset: Offset, currentScale: Float): Offset {
            val scaledWidth = containerWidth * currentScale
            val scaledHeight = containerHeight * currentScale

            val maxX = maxOf(0f, (scaledWidth - containerWidth) / 2f)
            val maxY = maxOf(0f, (scaledHeight - containerHeight) / 2f)

            return Offset(
                proposedOffset.x.coerceIn(-maxX, maxX),
                proposedOffset.y.coerceIn(-maxY, maxY),
            )
        }

        // Transformable state for pinch zoom
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val proposedScale = scale * zoomChange
            
            // Apply scale
            scale = proposedScale.coerceIn(MIN_SCALE, MAX_SCALE)
            
            // Apply pan with bounds checking
            val proposedOffset = offset + panChange
            offset = clampOffset(proposedOffset, scale)
            
            // Reset to center if zoomed out completely
            if (scale <= MIN_SCALE) {
                offset = Offset.Zero
            }
        }

        // Double-tap gesture handler
        val gestureModifier = Modifier.pointerInput(containerWidth, containerHeight) {
            detectTapGestures(
                onDoubleTap = { tapOffset ->
                    scope.launch {
                        if (scale <= MIN_SCALE + 0.05f) {
                            // Zoom IN to tap location
                            val targetScale = DOUBLE_TAP_ZOOM
                            
                            // Calculate offset to center on tap point
                            val centerX = containerWidth / 2f
                            val centerY = containerHeight / 2f
                            val targetOffset = Offset(
                                x = (centerX - tapOffset.x) * (targetScale - 1f),
                                y = (centerY - tapOffset.y) * (targetScale - 1f),
                            )
                            
                            // Animate zoom in
                            val startScale = scale
                            val startOffset = offset
                            val clampedTarget = clampOffset(targetOffset, targetScale)
                            
                            Animatable(0f).animateTo(1f) {
                                scale = lerp(startScale, targetScale, this.value)
                                offset = Offset(
                                    x = lerp(startOffset.x, clampedTarget.x, this.value),
                                    y = lerp(startOffset.y, clampedTarget.y, this.value),
                                )
                            }
                        } else {
                            // Zoom OUT to reset
                            val startScale = scale
                            val startOffset = offset
                            
                            Animatable(0f).animateTo(1f) {
                                scale = lerp(startScale, MIN_SCALE, this.value)
                                offset = Offset(
                                    x = lerp(startOffset.x, 0f, this.value),
                                    y = lerp(startOffset.y, 0f, this.value),
                                )
                            }
                        }
                    }
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Apply gesture first to capture taps
                .then(gestureModifier)
                // Only enable transformable when scale is appropriate
                .transformable(
                    state = transformState,
                    enabled = true, // Always enabled but HorizontalPager handles disable
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
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

            // Control buttons
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

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
