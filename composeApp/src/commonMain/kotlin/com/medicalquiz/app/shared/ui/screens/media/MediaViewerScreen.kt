package com.medicalquiz.app.shared.ui.screens.media

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
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
import com.medicalquiz.app.shared.ui.media.MediaType
import com.medicalquiz.app.shared.platform.StorageProvider
import com.medicalquiz.app.shared.ui.richtext.RichText
import com.medicalquiz.app.shared.ui.richtext.RichTextScaleProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

// Animation and interaction constants
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MIN_SCALE = 1f

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaViewerScreen(
    mediaFiles: List<String>,
    startIndex: Int = 0,
    mediaDescriptions: Map<String, MediaDescription> = emptyMap(),
    richTextScale: Float = 1f,
    onLinkClick: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    sharedTransitionKey: String? = null,
) {
    SharedTransitionLayout {
        MediaViewerContent(
            mediaFiles = mediaFiles,
            startIndex = startIndex,
            mediaDescriptions = mediaDescriptions,
            richTextScale = richTextScale,
            onLinkClick = onLinkClick,
            onBack = onBack,
            sharedTransitionKey = sharedTransitionKey,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedTransitionScope.MediaViewerContent(
    mediaFiles: List<String>,
    startIndex: Int,
    mediaDescriptions: Map<String, MediaDescription>,
    richTextScale: Float,
    onLinkClick: ((String) -> Unit)?,
    onBack: () -> Unit,
    sharedTransitionKey: String?,
) {
    if (mediaFiles.isEmpty()) {
        PlatformBackHandler(enabled = true, onBack = onBack)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.systemBars),
            contentAlignment = Alignment.Center,
        ) {
            UnsupportedContent(fileName = "No media", type = MediaType.UNKNOWN)
        }
        return
    }

    PlatformBackHandler(enabled = true, onBack = onBack)

    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { mediaFiles.size },
    )
    var isZoomed by rememberSaveable { mutableStateOf(false) }
    var showUI by rememberSaveable { mutableStateOf(true) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }

    val currentDescription = mediaDescriptions[mediaFiles.getOrNull(pagerState.currentPage)]

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        showOverlay = true
        showExplanation = false
    }

    val onToggleUI: () -> Unit = { showUI = !showUI }

    val currentFileName = mediaFiles.getOrNull(pagerState.currentPage) ?: ""
    val storageDir = remember { StorageProvider.getAppStorageDirectory() }
    val overlayPath by produceState<String?>(initialValue = null, currentFileName, storageDir) {
        value = withContext(Dispatchers.IO) {
            if (!currentFileName.startsWith("big_", ignoreCase = true)) return@withContext null
            val overlayFile = currentFileName.substringBeforeLast('.') + ".svg"
            val path = "$storageDir/media/$overlayFile"
            if (FileSystemHelper.exists(path)) path else null
        }
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (showUI) MaterialTheme.colorScheme.surface else Color.Black,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isZoomed,
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = lerp(0.5f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                        val scale = lerp(0.85f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                MediaContent(
                    fileName = mediaFiles[page],
                    isActivePage = pagerState.currentPage == page,
                    onZoomChanged = {
                        isZoomed = it
                        if (it) showUI = false
                    },
                    onSingleTap = onToggleUI,
                    overlayPath = if (page == pagerState.currentPage) overlayPath else null,
                    showOverlay = if (page == pagerState.currentPage) showOverlay else true,
                )
            }
        }

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                FilledIconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }

                if (mediaFiles.size > 1) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${mediaFiles.size}",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        val hasOverlay by derivedStateOf { overlayPath != null }
        val hasDescription = currentDescription != null

        AnimatedVisibility(
            visible = showUI && (hasOverlay || hasDescription),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            // ButtonGroup with overlay toggle and info clickable button
            Box(
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                ButtonGroup(
                    overflowIndicator = { },
                    expandedRatio = 0.1f,
                ) {
                    if (hasOverlay) {
                        // Toggle overlay visibility
                        toggleableItem(
                            checked = showOverlay,
                            label = "Overlay",
                            onCheckedChange = { showOverlay = it },
                            weight = 10.0f,
                            icon = {
                                Icon(
                                    imageVector = if (showOverlay) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = if (showOverlay) "Hide overlay" else "Show overlay",
                                )
                            },
                        )
                    }

                    if (hasDescription) {
                        // Info button - clickable (opens bottom sheet)
                        clickableItem(
                            label = "Info",
                            onClick = { showExplanation = true },
                            weight = 8.0f,
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Show info",
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (showExplanation && currentDescription != null) {
        ExplanationBottomSheet(
            description = currentDescription,
            richTextScale = richTextScale,
            onDismiss = { showExplanation = false },
            onLinkClick = onLinkClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExplanationBottomSheet(
    description: MediaDescription,
    richTextScale: Float,
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
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
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
                    style = MaterialTheme.typography.headlineSmallEmphasized,
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
                RichTextScaleProvider(proseScale = richTextScale) {
                    RichText(
                        html = description.description,
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClick = onLinkClick,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaContent(
    fileName: String,
    isActivePage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    overlayPath: String? = null,
    showOverlay: Boolean = true,
) {
    val mediaType = remember(fileName) { getMediaType(fileName) }
    val storageDir = remember { StorageProvider.getAppStorageDirectory() }
    val filePath = remember(fileName) { "$storageDir/media/$fileName" }

    val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val defaultSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fastSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    // Animated content for media type changes
    AnimatedContent(
        targetState = mediaType,
        transitionSpec = {
            fadeIn(animationSpec = defaultEffectsSpec) +
                scaleIn(initialScale = 0.9f, animationSpec = defaultSpatialSpec) togetherWith
                fadeOut(animationSpec = fastEffectsSpec) +
                scaleOut(targetScale = 1.1f, animationSpec = fastSpatialSpec)
        },
        label = "media_transition"
    ) { type ->
        when (type) {
            MediaType.IMAGE -> ImageContent(
                fileName = fileName,
                onZoomChanged = onZoomChanged,
                onSingleTap = onSingleTap,
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
private fun ImageContent(
    fileName: String,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
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

    var scale by rememberSaveable(fileName) { mutableFloatStateOf(MIN_SCALE) }
    var offsetX by rememberSaveable(fileName) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(fileName) { mutableFloatStateOf(0f) }
    var animationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Velocity tracking for fling
    var velocityX by remember { mutableFloatStateOf(0f) }
    var velocityY by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()
    val isZoomed by remember(scale) { derivedStateOf { scale > MIN_SCALE + 0.01f } }

    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    DisposableEffect(Unit) {
        onDispose {
            animationJob?.cancel()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }

        fun boundsFor(currentScale: Float): Offset {
            if (currentScale <= MIN_SCALE) return Offset.Zero
            val maxX = ((containerWidth * currentScale) - containerWidth) / 2f
            val maxY = ((containerHeight * currentScale) - containerHeight) / 2f
            return Offset(maxOf(0f, maxX), maxOf(0f, maxY))
        }

        fun clampOffset(offset: Offset, currentScale: Float): Offset {
            if (currentScale <= MIN_SCALE) return Offset.Zero
            val bounds = boundsFor(currentScale)
            return Offset(
                x = offset.x.coerceIn(-bounds.x, bounds.x),
                y = offset.y.coerceIn(-bounds.y, bounds.y),
            )
        }

        fun targetOffsetForDoubleTap(tapOffset: Offset, targetScale: Float): Offset {
            if (targetScale <= MIN_SCALE) return Offset.Zero
            val center = Offset(containerWidth / 2f, containerHeight / 2f)
            val raw = Offset(
                x = (center.x - tapOffset.x) * (targetScale - 1f),
                y = (center.y - tapOffset.y) * (targetScale - 1f),
            )
            return clampOffset(raw, targetScale)
        }

        val transformModifier = Modifier
            .pointerInput(containerWidth, containerHeight) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tapOffset ->
                        animationJob?.cancel()
                        animationJob = scope.launch {
                            val startScale = scale
                            val startOffset = Offset(offsetX, offsetY)
                            val zoomIn = scale <= MIN_SCALE + 0.05f
                            val targetScale = if (zoomIn) DOUBLE_TAP_ZOOM else MIN_SCALE
                            val targetOffset = targetOffsetForDoubleTap(
                                tapOffset = tapOffset,
                                targetScale = targetScale,
                            )

                            androidx.compose.animation.core.animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) { value, _ ->
                                scale = lerp(startScale, targetScale, value)
                                offsetX = lerp(startOffset.x, targetOffset.x, value)
                                offsetY = lerp(startOffset.y, targetOffset.y, value)
                            }
                        }
                    },
                )
            }
            .pointerInput(containerWidth, containerHeight) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var isTransforming = false
                    var previousTime = 0L
                    var lastCentroid = Offset.Zero

                    do {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val pointerCount = changes.count { it.pressed }
                        val currentTime = changes.firstOrNull()?.uptimeMillis ?: 0L

                        // Start transform on multi-touch or if already zoomed
                        if (pointerCount > 1 || (pointerCount > 0 && scale > MIN_SCALE + 0.01f)) {
                            isTransforming = true
                        }

                        if (!isTransforming) {
                            continue
                        }

                        // Calculate time delta for velocity
                        val timeDelta = if (previousTime > 0) (currentTime - previousTime).coerceAtLeast(1) else 1
                        previousTime = currentTime

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid()

                        // Only apply zoom from 2+ fingers to avoid centroid jumps
                        val currentOffset = Offset(offsetX, offsetY)
                        val previousScale = scale
                        val effectiveZoom = if (pointerCount >= 2) zoomChange else 1f
                        val nextScale = (previousScale * effectiveZoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        val center = Offset(containerWidth / 2f, containerHeight / 2f)

                        // Calculate offset with proper centroid handling
                        val updatedOffset = if (pointerCount >= 2 && effectiveZoom != 1f) {
                            val relativeToContent = (centroid - center - currentOffset) / previousScale
                            centroid - center - (relativeToContent * nextScale) + panChange
                        } else {
                            currentOffset + panChange
                        }

                        val clampedOffset = clampOffset(updatedOffset, nextScale)

                        // Track velocity for fling
                        if (pointerCount >= 1 && lastCentroid != Offset.Zero) {
                            val dx = (clampedOffset.x - currentOffset.x)
                            val dy = (clampedOffset.y - currentOffset.y)
                            velocityX = dx / timeDelta * 1000f
                            velocityY = dy / timeDelta * 1000f
                        }
                        if (pointerCount >= 2) {
                            lastCentroid = centroid
                        }

                        scale = nextScale
                        offsetX = clampedOffset.x
                        offsetY = clampedOffset.y

                        changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })

                    // Apply fling animation when gesture ends and zoomed in
                    if (isTransforming && scale > MIN_SCALE + 0.01f) {
                        val flingVelocityX = velocityX.coerceIn(-8000f, 8000f)
                        val flingVelocityY = velocityY.coerceIn(-8000f, 8000f)

                        if (flingVelocityX.absoluteValue > 500f || flingVelocityY.absoluteValue > 500f) {
                            animationJob?.cancel()
                            animationJob = scope.launch {
                                var animScale = scale
                                androidx.compose.animation.core.animate(
                                    initialValue = 1f,
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                ) { value, _ ->
                                    val targetX = offsetX + flingVelocityX * 0.016f * value
                                    val targetY = offsetY + flingVelocityY * 0.016f * value
                                    val clamped = clampOffset(Offset(targetX, targetY), animScale)
                                    offsetX = clamped.x
                                    offsetY = clamped.y
                                }
                            }
                        }
                    }

                    velocityX = 0f
                    velocityY = 0f
                }
            }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(transformModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentAlignment = Alignment.Center,
        ) {
            var isLoading by remember { mutableStateOf(true) }

            AsyncImage(
                model = filePath,
                contentDescription = fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onState = { state ->
                    isLoading = state is AsyncImagePainter.State.Loading
                },
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Loading…",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
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
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Unsupported Media",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
