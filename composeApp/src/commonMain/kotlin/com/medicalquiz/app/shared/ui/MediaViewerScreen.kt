package com.medicalquiz.app.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.Spring
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

// Animation and interaction constants
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val FADE_INTENSITY = 0.5f
private const val MIN_SCALE = 1f

// Swipe-to-dismiss constants (Google Photos / Apple Photos style)
private const val DISMISS_THRESHOLD = 100f // pixels to swipe before dismiss triggers
private const val DISMISS_VELOCITY_THRESHOLD = 800f // px/s - quick flick dismisses regardless of distance
private const val MAX_DISMISS_SCALE = 0.85f // image shrinks to this scale at max drag
private const val DRAG_RESISTANCE = 0.6f // rubber-band resistance factor

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

    // Swipe-to-dismiss state (Google Photos / Apple Photos style)
    val dismissOffsetY = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        // Reset dismiss offset when changing pages
        if (!isDismissing) {
            dismissOffsetY.snapTo(0f)
        }
    }

    // Calculate visual properties based on drag distance
    val dragProgress = (dismissOffsetY.value.absoluteValue / 300f).coerceIn(0f, 1f)
    val dismissScale = lerp(1f, MAX_DISMISS_SCALE, dragProgress)
    val backgroundAlpha = lerp(1f, 0f, dragProgress)

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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha),
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black.copy(alpha = backgroundAlpha)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Scale and translate together for natural feel
                        scaleX = dismissScale
                        scaleY = dismissScale
                        translationY = dismissOffsetY.value
                    }
                    .pointerInput(isZoomed) {
                        if (!isZoomed) {
                            var lastDragAmount = 0f
                            detectVerticalDragGestures(
                                onDragStart = { 
                                    dragVelocity = 0f
                                    lastDragAmount = 0f
                                },
                                onDragEnd = {
                                    scope.launch {
                                        val shouldDismiss = dismissOffsetY.value.absoluteValue > DISMISS_THRESHOLD ||
                                                dragVelocity.absoluteValue > DISMISS_VELOCITY_THRESHOLD
                                        
                                        if (shouldDismiss) {
                                            // Animate out in the direction of the swipe
                                            isDismissing = true
                                            val targetOffset = if (dismissOffsetY.value > 0) 1500f else -1500f
                                            dismissOffsetY.animateTo(
                                                targetValue = targetOffset,
                                                animationSpec = tween(durationMillis = 250)
                                            )
                                            onBack()
                                        } else {
                                            // Spring back to center
                                            dismissOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = SpringSpec(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        dismissOffsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = SpringSpec(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    // Apply rubber-band resistance
                                    val resistedDrag = dragAmount * DRAG_RESISTANCE
                                    // Track velocity (simple approximation)
                                    dragVelocity = dragAmount * 60f // ~60fps estimate
                                    scope.launch {
                                        dismissOffsetY.snapTo(dismissOffsetY.value + resistedDrag)
                                    }
                                }
                            )
                        }
                    },
                userScrollEnabled = !isZoomed && dismissOffsetY.value.absoluteValue < 10f,
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
