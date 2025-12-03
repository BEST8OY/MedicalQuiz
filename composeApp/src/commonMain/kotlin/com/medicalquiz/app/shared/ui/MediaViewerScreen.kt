package com.medicalquiz.app.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.produceState
import coil3.compose.AsyncImage
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.StorageProvider
import com.medicalquiz.app.shared.ui.richtext.RichText
import com.medicalquiz.app.shared.utils.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// Animation and interaction constants
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val FADE_INTENSITY = 0.5f
private const val MIN_SCALE = 1f
private const val DISMISS_THRESHOLD = 150f // pixels to swipe before dismiss triggers
private const val DISMISS_VELOCITY_THRESHOLD = 1000f // velocity threshold for quick swipe dismiss

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
    var isZoomed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Swipe-to-dismiss state
    val dismissOffset = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        // Reset dismiss offset when changing pages
        if (!isDismissing) {
            dismissOffset.snapTo(0f)
        }
    }

    // Calculate alpha based on dismiss offset for fade-out effect
    val dismissAlpha = 1f - (dismissOffset.value.absoluteValue / 500f).coerceIn(0f, 0.5f)

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
                            text = "${pagerState.currentPage + 1} / ${mediaFiles.size}",
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
                .background(Color.Black.copy(alpha = dismissAlpha)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, dismissOffset.value.roundToInt()) }
                    .graphicsLayer { alpha = dismissAlpha }
                    .pointerInput(isZoomed) {
                        if (!isZoomed) {
                            detectVerticalDragGestures(
                                onDragStart = { },
                                onDragEnd = {
                                    scope.launch {
                                        if (dismissOffset.value.absoluteValue > DISMISS_THRESHOLD) {
                                            // Animate out and dismiss
                                            isDismissing = true
                                            val targetOffset = if (dismissOffset.value > 0) 1000f else -1000f
                                            dismissOffset.animateTo(
                                                targetValue = targetOffset,
                                                animationSpec = tween(durationMillis = 200)
                                            )
                                            onBack()
                                        } else {
                                            // Snap back
                                            dismissOffset.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 200)
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        dismissOffset.animateTo(0f, animationSpec = tween(durationMillis = 200))
                                    }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    scope.launch {
                                        dismissOffset.snapTo(dismissOffset.value + dragAmount)
                                    }
                                }
                            )
                        }
                    },
                userScrollEnabled = !isZoomed,
                beyondViewportPageCount = 1,
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            alpha = 1f - (pageOffset.coerceIn(-1f, 1f).absoluteValue * FADE_INTENSITY)
                        },
                ) {
                    MediaContent(
                        fileName = mediaFiles[page],
                        description = mediaDescriptions[mediaFiles[page]],
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
    val filePath = remember(fileName) {
        "${StorageProvider.getAppStorageDirectory()}/media/$fileName"
    }

    // Load and sanitize HTML on background thread
    val htmlContent by produceState<String?>(initialValue = null, filePath) {
        value = withContext(Dispatchers.IO) {
            val raw = FileSystemHelper.readText(filePath)
            raw?.let(HtmlUtils::sanitizeForRichText)
        }
    }

    if (htmlContent == null) {
        // Still loading or failed
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Loading…", color = Color.White)
        }
    } else if (htmlContent!!.isBlank()) {
        UnsupportedContent(fileName = fileName, type = MediaType.HTML)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            RichText(
                html = htmlContent!!,
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
    val storageDir = remember { StorageProvider.getAppStorageDirectory() }
    val filePath = remember(fileName) { "$storageDir/media/$fileName" }

    // Check file existence on background thread
    val fileExists by produceState(initialValue = true, filePath) {
        value = withContext(Dispatchers.IO) { FileSystemHelper.exists(filePath) }
    }

    if (!fileExists) {
        UnsupportedContent(fileName = fileName, type = MediaType.IMAGE)
        return
    }

    val scope = rememberCoroutineScope()

    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var scale by rememberSaveable { mutableFloatStateOf(MIN_SCALE) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f)
    }

    // Resolve overlay path on background thread
    val overlayPath by produceState<String?>(initialValue = null, fileName, storageDir) {
        value = withContext(Dispatchers.IO) {
            if (!fileName.startsWith("big_", ignoreCase = true)) return@withContext null
            val overlayFile = fileName.substringBeforeLast('.') + ".svg"
            val path = "$storageDir/media/$overlayFile"
            if (FileSystemHelper.exists(path)) path else null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }

        // Clamp offset to keep image within bounds
        fun clampOffset(proposedX: Float, proposedY: Float, currentScale: Float): Pair<Float, Float> {
            val scaledWidth = containerWidth * currentScale
            val scaledHeight = containerHeight * currentScale

            val maxX = maxOf(0f, (scaledWidth - containerWidth) / 2f)
            val maxY = maxOf(0f, (scaledHeight - containerHeight) / 2f)

            return Pair(
                proposedX.coerceIn(-maxX, maxX),
                proposedY.coerceIn(-maxY, maxY),
            )
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
            if (scale > MIN_SCALE) {
                val (clampedX, clampedY) = clampOffset(offsetX + panChange.x, offsetY + panChange.y, scale)
                offsetX = clampedX
                offsetY = clampedY
            } else {
                offsetX = 0f
                offsetY = 0f
            }
        }

        val gestureModifier = Modifier.pointerInput(containerWidth, containerHeight) {
            detectTapGestures(
                onDoubleTap = { tapOffset ->
                    scope.launch {
                        val startScale = scale
                        val startOffsetX = offsetX
                        val startOffsetY = offsetY
                        val (targetScale, targetX, targetY) = if (scale <= MIN_SCALE + 0.05f) {
                            Triple(
                                DOUBLE_TAP_ZOOM,
                                (containerWidth / 2f - tapOffset.x) * (DOUBLE_TAP_ZOOM - 1f),
                                (containerHeight / 2f - tapOffset.y) * (DOUBLE_TAP_ZOOM - 1f)
                            )
                        } else {
                            Triple(MIN_SCALE, 0f, 0f)
                        }
                        val (clampedTargetX, clampedTargetY) = clampOffset(targetX, targetY, targetScale)
                        Animatable(0f).animateTo(1f) {
                            scale = lerp(startScale, targetScale, this.value)
                            offsetX = lerp(startOffsetX, clampedTargetX, this.value)
                            offsetY = lerp(startOffsetY, clampedTargetY, this.value)
                        }
                    }
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(
                    state = transformState,
                    canPan = { scale > MIN_SCALE }
                )
                .then(gestureModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
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
        }

        // Icons outside the transformed area so they stay fixed on screen
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
