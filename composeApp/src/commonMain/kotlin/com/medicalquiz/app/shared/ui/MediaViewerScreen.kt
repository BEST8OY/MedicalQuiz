package com.medicalquiz.app.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// Animation and interaction constants
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MIN_SCALE = 1f
private const val BOUNDARY_RESISTANCE = 0.55f
private const val FLING_FRICTION = 0.92f
private const val MIN_FLING_VELOCITY = 100f

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
    val density = LocalDensity.current
    
    // iOS-style pull-to-dismiss state
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val dismissProgress by remember { derivedStateOf { (dismissOffsetY.absoluteValue / 300f).coerceIn(0f, 1f) } }
    
    // Dismiss animation - complete the dismiss flow with Navigation 3 integration
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            // Animate the dismiss completion before calling onBack
            // This gives NavDisplay time to show the predictive back transition
            val targetOffset = if (dismissOffsetY > 0) 800f else -800f
            androidx.compose.animation.core.animate(
                initialValue = dismissOffsetY,
                targetValue = targetOffset,
                animationSpec = tween(200)
            ) { value, _ ->
                dismissOffsetY = value
            }
            onBack()
        }
    }
    
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

    // Toggle UI on single tap
    val onToggleUI: () -> Unit = { showUI = !showUI }

    // Calculate overlay path for current image
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

    // Enhanced pull-to-dismiss gesture with improved physics
    val dismissThreshold = with(density) { 120.dp.toPx() }
    val dismissVelocityThreshold = with(density) { 600.dp.toPx() }

    // Dynamic background color based on UI visibility
    val backgroundColor by animateColorAsState(
        targetValue = if (showUI) MaterialTheme.colorScheme.surface else Color.Black,
        animationSpec = tween(400)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {

        // Main content with enhanced pull-to-dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dismissOffsetY.roundToInt()) }
                .pointerInput(isZoomed) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        
                        // Only start dismiss gesture if not zoomed and starting from center area
                        if (isZoomed || isDismissing) return@awaitEachGesture
                        
                        var dragStarted = false
                        val velocityTracker = VelocityTracker()

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            // Check if vertical movement dominates - lower threshold for easier trigger
                            val totalDeltaY = change.position.y - down.position.y
                            val totalDeltaX = kotlin.math.abs(change.position.x - down.position.x)
                            val absTotalDeltaY = kotlin.math.abs(totalDeltaY)

                            if (!dragStarted) {
                                if (absTotalDeltaY > 8 && absTotalDeltaY > totalDeltaX * 1.2f) {
                                    dragStarted = true
                                }
                            }

                            if (dragStarted) {
                                // Apply resistance to the drag - compute based on current offset
                                val deltaY = change.position.y - change.previousPosition.y
                                val progress = (dismissOffsetY.absoluteValue / dismissThreshold).coerceIn(0f, 2f)
                                val resistanceFactor = kotlin.math.cos(progress * kotlin.math.PI / 4.0).toFloat() * 0.7f + 0.3f
                                val resistedDelta = deltaY * resistanceFactor

                                dismissOffsetY += resistedDelta
                                velocityTracker.addPointerInputChange(change)
                            }

                            change.consume()
                        } while (event.changes.any { it.pressed })
                        
                        if (dragStarted) {
                            val velocity = velocityTracker.calculateVelocity()
                            val absOffset = kotlin.math.abs(dismissOffsetY)
                            val absVelocity = kotlin.math.abs(velocity.y)
                            
                            // Check if we should dismiss
                            val shouldDismiss = absOffset > dismissThreshold || 
                                              absVelocity > dismissVelocityThreshold
                            
                            if (shouldDismiss) {
                                // Check velocity direction matches offset direction
                                val velocityMatchesDirection = 
                                    (dismissOffsetY > 0 && velocity.y > 0) || 
                                    (dismissOffsetY < 0 && velocity.y < 0)
                                
                                if (absOffset > dismissThreshold * 0.5f || velocityMatchesDirection) {
                                    isDismissing = true
                                } else {
                                    // Spring back
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
                            } else {
                                // Spring back
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
                    }
                }
                .graphicsLayer {
                    // Smooth scale and fade based on dismiss progress
                    val scale = 1f - (dismissProgress * 0.12f).coerceIn(0f, 0.12f)
                    scaleX = scale
                    scaleY = scale
                    // Fade out faster than scale reduces
                    alpha = 1f - (dismissProgress * 0.5f).coerceIn(0f, 0.5f)
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
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    // Back button
                    FilledIconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${mediaFiles.size}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Segmented button for info and overlay - at bottom
            val hasOverlay by derivedStateOf { overlayPath != null }
            val hasDescription = currentDescription != null

            AnimatedVisibility(
                visible = showUI && dismissProgress < 0.1f && (hasOverlay || hasDescription),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
            ) {
                when {
                    hasOverlay && hasDescription -> {
                        MultiChoiceSegmentedButtonRow {
                            SegmentedButton(
                                checked = showOverlay,
                                onCheckedChange = { showOverlay = it },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = {
                                    SegmentedButtonDefaults.Icon(showOverlay) {
                                        Icon(
                                            imageVector = if (showOverlay) {
                                                Icons.Filled.Visibility
                                            } else {
                                                Icons.Filled.VisibilityOff
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                label = { Text("Overlay") },
                            )

                            SegmentedButton(
                                checked = showExplanation,
                                onCheckedChange = { showExplanation = it },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                icon = {
                                    SegmentedButtonDefaults.Icon(showExplanation) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                label = { Text("Info") },
                            )
                        }
                    }

                    hasOverlay -> {
                        FilterChip(
                            selected = showOverlay,
                            onClick = { showOverlay = !showOverlay },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showOverlay) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            label = { Text("Overlay") },
                        )
                    }

                    hasDescription -> {
                        FilterChip(
                            selected = showExplanation,
                            onClick = { showExplanation = !showExplanation },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            label = { Text("Info") },
                        )
                    }
                }
            }
        }

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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
    var flingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var zoomAnimationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var scale by rememberSaveable { mutableFloatStateOf(MIN_SCALE) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

    val isZoomed by remember { derivedStateOf { scale > 1.05f } }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f)
    }

    DisposableEffect(Unit) {
        onDispose {
            flingJob?.cancel()
            zoomAnimationJob?.cancel()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }

        fun calculateBoundaries(currentScale: Float): Pair<Float, Float> {
            if (currentScale <= MIN_SCALE) return Pair(0f, 0f)
            val scaledWidth = containerWidth * currentScale
            val scaledHeight = containerHeight * currentScale
            val maxX = maxOf(0f, (scaledWidth - containerWidth) / 2f)
            val maxY = maxOf(0f, (scaledHeight - containerHeight) / 2f)
            return Pair(maxX, maxY)
        }

        fun clampOffsetWithResistance(proposedX: Float, proposedY: Float, currentScale: Float): Pair<Float, Float> {
            if (currentScale <= MIN_SCALE) return Pair(0f, 0f)

            val (maxX, maxY) = calculateBoundaries(currentScale)

            val clampedX = when {
                proposedX < -maxX -> -maxX + (proposedX + maxX) * BOUNDARY_RESISTANCE
                proposedX > maxX -> maxX + (proposedX - maxX) * BOUNDARY_RESISTANCE
                else -> proposedX
            }

            val clampedY = when {
                proposedY < -maxY -> -maxY + (proposedY + maxY) * BOUNDARY_RESISTANCE
                proposedY > maxY -> maxY + (proposedY - maxY) * BOUNDARY_RESISTANCE
                else -> proposedY
            }

            return Pair(clampedX, clampedY)
        }

        fun clampOffsetStrict(proposedX: Float, proposedY: Float, currentScale: Float): Pair<Float, Float> {
            if (currentScale <= MIN_SCALE) return Pair(0f, 0f)
            val (maxX, maxY) = calculateBoundaries(currentScale)
            return Pair(
                proposedX.coerceIn(-maxX, maxX),
                proposedY.coerceIn(-maxY, maxY)
            )
        }

        fun performFling(velocityX: Float, velocityY: Float) {
            if (!isZoomed) return

            flingJob?.cancel()
            flingJob = scope.launch {
                var currentVelocityX = velocityX
                var currentVelocityY = velocityY

                while (kotlin.math.abs(currentVelocityX) > MIN_FLING_VELOCITY ||
                       kotlin.math.abs(currentVelocityY) > MIN_FLING_VELOCITY) {

                    val newOffsetX = offsetX + currentVelocityX * 0.016f
                    val newOffsetY = offsetY + currentVelocityY * 0.016f

                    val (maxX, maxY) = calculateBoundaries(scale)

                    val (clampedX, clampedY) = if (newOffsetX < -maxX || newOffsetX > maxX ||
                                                    newOffsetY < -maxY || newOffsetY > maxY) {
                        clampOffsetStrict(newOffsetX, newOffsetY, scale)
                    } else {
                        Pair(newOffsetX, newOffsetY)
                    }

                    offsetX = clampedX
                    offsetY = clampedY

                    currentVelocityX *= FLING_FRICTION
                    currentVelocityY *= FLING_FRICTION

                    if (clampedX != newOffsetX) currentVelocityX = 0f
                    if (clampedY != newOffsetY) currentVelocityY = 0f

                    kotlinx.coroutines.delay(16)
                }

                val (maxX, maxY) = calculateBoundaries(scale)
                if (offsetX < -maxX || offsetX > maxX || offsetY < -maxY || offsetY > maxY) {
                    Animatable(0f).animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) {
                        val targetX = offsetX.coerceIn(-maxX, maxX)
                        val targetY = offsetY.coerceIn(-maxY, maxY)
                        offsetX = lerp(offsetX, targetX, value)
                        offsetY = lerp(offsetY, targetY, value)
                    }
                }
            }
        }

        val gestureModifier = Modifier
            .pointerInput(containerWidth, containerHeight) {
                awaitEachGesture {
                    val velocityTracker = VelocityTracker()
                    awaitFirstDown()
                    flingJob?.cancel()
                    zoomAnimationJob?.cancel()

                    var panStarted = false

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (pointerCount >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid()

                            val previousScale = scale
                            val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)

                            if (centroid != Offset.Unspecified && centroid != Offset.Zero) {
                                // Calculate zoom offset to keep centroid stationary
                                // The centroid should stay at the same screen position
                                val centroidScreenX = centroid.x
                                val centroidScreenY = centroid.y

                                // Where centroid was relative to center before zoom
                                val relativeX = centroidScreenX - containerWidth / 2f - offsetX
                                val relativeY = centroidScreenY - containerHeight / 2f - offsetY

                                // After zoom, we need to adjust offset so centroid stays in place
                                val newRelativeX = relativeX * (newScale / previousScale)
                                val newRelativeY = relativeY * (newScale / previousScale)

                                val zoomOffsetX = relativeX - newRelativeX
                                val zoomOffsetY = relativeY - newRelativeY

                                val proposedX = offsetX + pan.x + zoomOffsetX
                                val proposedY = offsetY + pan.y + zoomOffsetY

                                if (newScale > MIN_SCALE) {
                                    val (clampedX, clampedY) = clampOffsetWithResistance(proposedX, proposedY, newScale)
                                    offsetX = clampedX
                                    offsetY = clampedY
                                } else {
                                    // Reset to center when fully zoomed out
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                            scale = newScale
                            panStarted = true
                        } else if (pointerCount == 1) {
                            val change = event.changes.first()
                            val panDelta = change.position - change.previousPosition

                            if (scale > MIN_SCALE) {
                                val proposedX = offsetX + panDelta.x
                                val proposedY = offsetY + panDelta.y
                                val (clampedX, clampedY) = clampOffsetWithResistance(proposedX, proposedY, scale)
                                offsetX = clampedX
                                offsetY = clampedY
                                velocityTracker.addPointerInputChange(change)
                            }
                        }

                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })

                    if (panStarted) {
                        val velocity = velocityTracker.calculateVelocity()
                        performFling(velocity.x, velocity.y)
                    }
                }
            }
            .pointerInput(containerWidth, containerHeight) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tapOffset ->
                        zoomAnimationJob?.cancel()
                        zoomAnimationJob = scope.launch {
                            val startScale = scale
                            val startOffsetX = offsetX
                            val startOffsetY = offsetY

                            val shouldZoomIn = scale <= MIN_SCALE + 0.05f
                            val targetScale = if (shouldZoomIn) DOUBLE_TAP_ZOOM else MIN_SCALE
                            val targetX = if (shouldZoomIn) {
                                (containerWidth / 2f - tapOffset.x) * (DOUBLE_TAP_ZOOM - 1f)
                            } else 0f
                            val targetY = if (shouldZoomIn) {
                                (containerHeight / 2f - tapOffset.y) * (DOUBLE_TAP_ZOOM - 1f)
                            } else 0f

                            val (clampedTargetX, clampedTargetY) = clampOffsetStrict(targetX, targetY, targetScale)

                            try {
                                Animatable(0f).animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) {
                                    scale = lerp(startScale, targetScale, value)
                                    offsetX = lerp(startOffsetX, clampedTargetX, value)
                                    offsetY = lerp(startOffsetY, clampedTargetY, value)
                                }
                            } catch (_: kotlinx.coroutines.CancellationException) {
                            }
                        }
                    }
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
