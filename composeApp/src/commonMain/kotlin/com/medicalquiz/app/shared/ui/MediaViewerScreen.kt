package com.medicalquiz.app.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.produceState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.medicalquiz.app.shared.data.MediaDescription
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.StorageProvider
import com.medicalquiz.app.shared.ui.richtext.RichText
import com.medicalquiz.app.shared.utils.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// Animation and interaction constants
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val FADE_INTENSITY = 0.3f
private const val MIN_SCALE = 1f
private const val UI_HIDE_DELAY_MS = 3000L

// Semi-transparent overlay colors
private val scrimColor = Color.Black.copy(alpha = 0.6f)
private val gradientTop = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
)
private val gradientBottom = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
)

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
    var showUI by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Auto-hide UI after delay
    LaunchedEffect(showUI, pagerState.currentPage) {
        if (showUI && !isZoomed) {
            delay(UI_HIDE_DELAY_MS)
            showUI = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        showUI = true
    }

    // Toggle UI on single tap (handled in ImageContent)
    val onToggleUI: () -> Unit = { showUI = !showUI }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isZoomed,
            beyondViewportPageCount = 1,
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        // Smooth parallax and fade effect
                        alpha = lerp(0.5f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                        val scale = lerp(0.85f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                MediaContent(
                    fileName = mediaFiles[page],
                    description = mediaDescriptions[mediaFiles[page]],
                    onZoomChanged = { 
                        isZoomed = it
                        if (it) showUI = false
                    },
                    onSingleTap = onToggleUI,
                    showUI = showUI,
                )
            }
        }

        // Top bar with gradient background
        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gradientTop)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                // Back button
                FilledIconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }

                // Page indicator pill
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${mediaFiles.size}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom page dots indicator (for multiple images)
        if (mediaFiles.size > 1) {
            AnimatedVisibility(
                visible = showUI,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(gradientBottom)
                        .padding(bottom = 24.dp, top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PageIndicatorDots(
                        pageCount = mediaFiles.size,
                        currentPage = pagerState.currentPage,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun PageIndicatorDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    // Show max 7 dots, with ellipsis effect for many pages
    val maxDots = 7
    val showDots = pageCount <= maxDots
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDots) {
            repeat(pageCount) { index ->
                val isSelected = index == currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color.White
                            else Color.White.copy(alpha = 0.4f)
                        )
                )
            }
        } else {
            // For many pages, show simplified indicator
            Text(
                text = "${currentPage + 1} of $pageCount",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MediaContent(
    fileName: String,
    description: MediaDescription?,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    showUI: Boolean,
) {
    val mediaType = remember(fileName) { getMediaType(fileName) }

    when (mediaType) {
        MediaType.IMAGE -> ImageContent(
            fileName = fileName,
            description = description,
            onZoomChanged = onZoomChanged,
            onSingleTap = onSingleTap,
            showUI = showUI,
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
        // Loading state with subtle animation
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Simple loading indicator
                Text(
                    text = "Loading…",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
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
    onSingleTap: () -> Unit,
    showUI: Boolean,
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
    var isLoading by remember { mutableStateOf(true) }

    val isZoomed by remember { derivedStateOf { scale > 1.05f } }

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
                onTap = { onSingleTap() },
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
                        Animatable(0f).animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) {
                            scale = lerp(startScale, targetScale, this.value)
                            offsetX = lerp(startOffsetX, clampedTargetX, this.value)
                            offsetY = lerp(startOffsetY, clampedTargetY, this.value)
                        }
                    }
                },
            )
        }

        // Image container with zoom/pan
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
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = filePath,
                contentDescription = fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onState = { state ->
                    isLoading = state is AsyncImagePainter.State.Loading
                }
            )

            if (overlayPath != null && showOverlay) {
                AsyncImage(
                    model = overlayPath,
                    contentDescription = "Overlay",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading…",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Floating action buttons (only when UI is visible)
        AnimatedVisibility(
            visible = showUI && !isZoomed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Info button
                if (description != null) {
                    FilledIconButton(
                        onClick = { showExplanation = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Show explanation",
                        )
                    }
                }
            }
        }

        // Overlay toggle button (top-start)
        if (overlayPath != null) {
            AnimatedVisibility(
                visible = showUI && !isZoomed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                FilledIconButton(
                    onClick = { showOverlay = !showOverlay },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (showOverlay) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f),
                        contentColor = if (showOverlay) Color.Black else Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (showOverlay) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (showOverlay) "Hide overlay" else "Show overlay",
                    )
                }
            }
        }

        // Zoom indicator (shows current zoom level when zoomed)
        AnimatedVisibility(
            visible = isZoomed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomIn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${(scale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Explanation dialog with improved styling
    if (showExplanation && description != null) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) {
                    Text("Close")
                }
            },
            title = {
                Text(
                    text = description.title.ifBlank { "Explanation" },
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    RichText(
                        html = description.description,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Unsupported Media",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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
