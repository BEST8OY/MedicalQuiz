package com.medicalquiz.app.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// Animation and interaction constants
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MIN_SCALE = 1f
private const val DISMISS_THRESHOLD = 0.3f
private const val DISMISS_VELOCITY_THRESHOLD = 500f

// Semi-transparent overlay colors
private val scrimColor = Color.Black.copy(alpha = 0.6f)
private val gradientTop = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
)
private val gradientBottom = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaFiles: List<String>,
    startIndex: Int = 0,
    mediaDescriptions: Map<String, MediaDescription> = emptyMap(),
    onLinkClick: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    sharedTransitionKey: String? = null,
) {
    SharedTransitionLayout {
        MediaViewerContent(
            mediaFiles = mediaFiles,
            startIndex = startIndex,
            mediaDescriptions = mediaDescriptions,
            onLinkClick = onLinkClick,
            onBack = onBack,
            sharedTransitionKey = sharedTransitionKey,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SharedTransitionScope.MediaViewerContent(
    mediaFiles: List<String>,
    startIndex: Int,
    mediaDescriptions: Map<String, MediaDescription>,
    onLinkClick: ((String) -> Unit)?,
    onBack: () -> Unit,
    sharedTransitionKey: String?,
) {
    PlatformBackHandler(enabled = true, onBack = onBack)

    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { mediaFiles.size }
    )
    var isZoomed by rememberSaveable { mutableStateOf(false) }
    var showUI by rememberSaveable { mutableStateOf(true) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    // iOS-style pull-to-dismiss state
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val dismissProgress by remember { derivedStateOf { (dismissOffsetY.absoluteValue / 300f).coerceIn(0f, 1f) } }
    val animatedDismissProgress = remember { Animatable(0f) }
    
    // Current page's description
    val currentDescription = mediaDescriptions[mediaFiles.getOrNull(pagerState.currentPage)]

    // Reset zoom and overlay when changing pages
    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        showOverlay = true
    }
    
    // Cleanup when leaving composition
    DisposableEffect(Unit) {
        onDispose {
            isZoomed = false
            showUI = true
            showExplanation = false
            showOverlay = true
        }
    }

    // Handle dismiss completion
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            val targetOffset = if (dismissOffsetY > 0) 1000f else -1000f
            animatedDismissProgress.animateTo(
                targetValue = targetOffset,
                animationSpec = tween(150, easing = FastOutSlowInEasing)
            )
            onBack()
        } else {
            animatedDismissProgress.animateTo(0f, spring())
        }
    }

    // Toggle UI on single tap
    val onToggleUI: () -> Unit = { showUI = !showUI }

    // Calculate overlay path for current image with caching (limit to 50 entries)
    val currentFileName = mediaFiles.getOrNull(pagerState.currentPage) ?: ""
    val storageDir = remember { StorageProvider.getAppStorageDirectory() }
    val overlayCache = remember { ConcurrentHashMap<String, String?>() }
    val overlayPath by produceState<String?>(initialValue = null, currentFileName, storageDir) {
        // Clear cache if it grows too large
        if (overlayCache.size > 50) {
            overlayCache.clear()
        }
        value = overlayCache.getOrPut(currentFileName) {
            withContext(Dispatchers.IO) {
                if (!currentFileName.startsWith("big_", ignoreCase = true)) return@withContext null
                val overlayFile = currentFileName.substringBeforeLast('.') + ".svg"
                val path = "$storageDir/media/$overlayFile"
                if (FileSystemHelper.exists(path)) path else null
            }
        }
    }

    // Draggable state for pull-to-dismiss with resistance
    val dismissDragState = rememberDraggableState { delta ->
        if (!isZoomed) {
            val resistance = 1 - (dismissOffsetY.absoluteValue / 800f).coerceIn(0f, 0.7f)
            dismissOffsetY += delta * resistance
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Background scrim that fades during dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - dismissProgress * 0.8f))
        )

        // Main content with pull-to-dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dismissOffsetY.roundToInt()) }
                .draggable(
                    state = dismissDragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val progress = dismissOffsetY.absoluteValue / 400f
                        val velocityThresholdMet = velocity.absoluteValue > DISMISS_VELOCITY_THRESHOLD
                        
                        if (progress > DISMISS_THRESHOLD || velocityThresholdMet) {
                            isDismissing = true
                        } else {
                            scope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = dismissOffsetY,
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) { value, _ ->
                                    dismissOffsetY = value
                                }
                            }
                        }
                    }
                )
                .graphicsLayer {
                    val scale = 1f - (dismissProgress * 0.15f).coerceIn(0f, 0.15f)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (dismissProgress * 0.3f).coerceIn(0f, 0.3f)
                }
        ) {
            // Main pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed && dismissProgress < 0.1f,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
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
                        isActivePage = pagerState.currentPage == page,
                        onZoomChanged = { 
                            isZoomed = it
                            if (it) showUI = false
                        },
                        onSingleTap = onToggleUI,
                        showUI = showUI,
                        onLinkClick = onLinkClick,
                        overlayPath = if (page == pagerState.currentPage) overlayPath else null,
                        showOverlay = if (page == pagerState.currentPage) showOverlay else true,
                    )
                }
            }

            // Top bar - back button and counter
            AnimatedVisibility(
                visible = showUI && dismissProgress < 0.1f,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(gradientTop)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    // Back button
                    FilledIconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart),
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

                    // Page counter (center, top)
                    if (mediaFiles.size > 1) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = MaterialTheme.shapes.medium,
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
            }

            // Segmented button for info and overlay - at bottom
            val hasOverlay by derivedStateOf { overlayPath != null }
            val hasDescription = currentDescription != null
            val hasBoth = hasOverlay && hasDescription
            
            AnimatedVisibility(
                visible = showUI && dismissProgress < 0.1f && (hasOverlay || hasDescription),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
            ) {
                // Material3 Segmented Button Container
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color.Black.copy(alpha = 0.75f),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (hasOverlay) {
                            // Overlay toggle button
                            val isOverlayActive = showOverlay
                            Surface(
                                onClick = { showOverlay = !showOverlay },
                                shape = MaterialTheme.shapes.large,
                                color = if (isOverlayActive) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    Color.Transparent,
                                tonalElevation = if (isOverlayActive) 2.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOverlayActive)
                                            Icons.Filled.Visibility
                                        else
                                            Icons.Filled.VisibilityOff,
                                        contentDescription = if (isOverlayActive) "Hide overlay" else "Show overlay",
                                        tint = if (isOverlayActive)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Overlay",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isOverlayActive) 
                                            MaterialTheme.colorScheme.onPrimaryContainer 
                                        else 
                                            Color.White.copy(alpha = 0.9f),
                                        fontWeight = if (isOverlayActive) 
                                            FontWeight.SemiBold 
                                        else 
                                            FontWeight.Medium
                                    )
                                }
                            }
                        }
                        
                        if (hasDescription) {
                            // Info button - always visible and styled
                            Surface(
                                onClick = { showExplanation = true },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Show information",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Info",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pull-to-dismiss visual feedback (Google Gallery style - no text hints,
        // just the visual feedback of content scaling and fading)
    }

    // Explanation bottom sheet (ModalBottomSheet instead of AlertDialog)
    if (showExplanation && currentDescription != null) {
        ExplanationBottomSheet(
            description = currentDescription,
            onDismiss = { showExplanation = false },
            onLinkClick = onLinkClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplanationBottomSheet(
    description: MediaDescription,
    onDismiss: () -> Unit,
    onLinkClick: ((String) -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
        tonalElevation = 6.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = description.title.ifBlank { "Explanation" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                RichText(
                    html = description.description,
                    modifier = Modifier.fillMaxWidth(),
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

@Composable
private fun MediaContent(
    fileName: String,
    description: MediaDescription?,
    isActivePage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    showUI: Boolean,
    onLinkClick: ((String) -> Unit)?,
    overlayPath: String? = null,
    showOverlay: Boolean = true,
) {
    val mediaType = remember(fileName) { getMediaType(fileName) }
    val storageDir = remember { StorageProvider.getAppStorageDirectory() }
    val filePath = remember(fileName) { "$storageDir/media/$fileName" }

    // Animated content for media type changes
    AnimatedContent(
        targetState = mediaType,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) + 
            scaleIn(initialScale = 0.9f, animationSpec = tween(300)) togetherWith
            fadeOut(animationSpec = tween(200)) + 
            scaleOut(targetScale = 1.1f, animationSpec = tween(200))
        },
        label = "media_transition"
    ) { type ->
        when (type) {
            MediaType.IMAGE -> ImageContent(
                fileName = fileName,
                description = description,
                onZoomChanged = onZoomChanged,
                onSingleTap = onSingleTap,
                showUI = showUI,
                overlayPath = overlayPath,
                showOverlay = showOverlay,
            )
            MediaType.VIDEO -> VideoContent(
                filePath = filePath,
                fileName = fileName,
                isActivePage = isActivePage
            )
            MediaType.AUDIO -> AudioContent(
                filePath = filePath,
                fileName = fileName,
                isActivePage = isActivePage
            )
            MediaType.HTML -> HtmlContent(fileName = fileName, onLinkClick = onLinkClick)
            else -> UnsupportedContent(fileName = fileName, type = mediaType)
        }
    }
}

@Composable
private fun VideoContent(filePath: String, fileName: String, isActivePage: Boolean) {
    val fileExists by produceState(initialValue = true, filePath) {
        value = withContext(Dispatchers.IO) { FileSystemHelper.exists(filePath) }
    }

    if (!fileExists) {
        UnsupportedContent(fileName = fileName, type = MediaType.VIDEO)
        return
    }

    VideoPlayer(
        filePath = filePath,
        modifier = Modifier.fillMaxSize(),
        isActivePage = isActivePage
    )
}

@Composable
private fun AudioContent(filePath: String, fileName: String, isActivePage: Boolean) {
    val fileExists by produceState(initialValue = true, filePath) {
        value = withContext(Dispatchers.IO) { FileSystemHelper.exists(filePath) }
    }

    if (!fileExists) {
        UnsupportedContent(fileName = fileName, type = MediaType.AUDIO)
        return
    }

    AudioPlayer(
        filePath = filePath,
        modifier = Modifier.fillMaxSize(),
        isActivePage = isActivePage
    )
}

@Composable
private fun HtmlContent(fileName: String, onLinkClick: ((String) -> Unit)?) {
    val filePath = remember(fileName) {
        "${StorageProvider.getAppStorageDirectory()}/media/$fileName"
    }

    val htmlContent by produceState<String?>(initialValue = null, filePath) {
        value = withContext(Dispatchers.IO) {
            val raw = FileSystemHelper.readText(filePath)
            raw?.let(HtmlUtils::sanitizeForRichText)
        }
    }

    AnimatedContent(
        targetState = htmlContent,
        transitionSpec = { fadeIn() togetherWith fadeOut() }
    ) { content ->
        when {
            content == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Loading…",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            content.isBlank() -> {
                UnsupportedContent(fileName = fileName, type = MediaType.HTML)
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    RichText(
                        html = content,
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClick = onLinkClick,
                    )
                }
            }
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
    overlayPath: String? = null,
    showOverlay: Boolean = true,
) {
    val storageDir = remember { StorageProvider.getAppStorageDirectory() }
    val filePath = remember(fileName) { "$storageDir/media/$fileName" }

    val fileExists by produceState(initialValue = true, filePath) {
        value = withContext(Dispatchers.IO) { FileSystemHelper.exists(filePath) }
    }

    if (!fileExists) {
        UnsupportedContent(fileName = fileName, type = MediaType.IMAGE)
        return
    }

    val scope = rememberCoroutineScope()
    var animationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var scale by rememberSaveable { mutableFloatStateOf(MIN_SCALE) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

    val isZoomed by remember { derivedStateOf { scale > 1.05f } }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            animationJob?.cancel()
            animationJob = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }

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

        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
            val oldScale = scale
            val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
            
            if (newScale > MIN_SCALE) {
                val newOffsetX = offsetX + panChange.x
                val newOffsetY = offsetY + panChange.y
                
                val (clampedX, clampedY) = clampOffset(newOffsetX, newOffsetY, newScale)
                offsetX = clampedX
                offsetY = clampedY
            } else {
                offsetX = 0f
                offsetY = 0f
            }
            scale = newScale
        }

        val gestureModifier = Modifier
            .transformable(
                state = transformableState,
                lockRotationOnZoomPan = true,
                canPan = { scale > MIN_SCALE + 0.01f }
            )
            .pointerInput(containerWidth, containerHeight) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tapOffset ->
                        animationJob?.cancel()
                        animationJob = scope.launch {
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
                            try {
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
                            } catch (_: kotlinx.coroutines.CancellationException) {
                                // Animation cancelled
                            }
                        }
                    },
                )
            }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentAlignment = Alignment.Center
        ) {
            var isLoading by remember { mutableStateOf(true) }
            
            AsyncImage(
                model = filePath,
                contentDescription = fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onState = { state ->
                    isLoading = state is AsyncImagePainter.State.Loading
                }
            )

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

            if (overlayPath != null && showOverlay) {
                AsyncImage(
                    model = overlayPath,
                    contentDescription = "Overlay",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

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
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
            }
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
    return com.medicalquiz.app.shared.utils.MediaTypeUtils.fromFileName(fileName)
}

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
