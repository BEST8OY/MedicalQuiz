package com.medicalquiz.app.shared.ui

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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
private const val DISMISS_COMPLETE_OFFSET = 800f
private const val DISMISS_PROGRESS_DISTANCE = 300f
private const val DRAG_START_DISTANCE = 8f
private const val VERTICAL_DRAG_DOMINANCE = 1.2f

private enum class GestureOwner {
    None,
    ImageTransform,
    Dismiss,
}

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
        pageCount = { mediaFiles.size }
    )
    var isZoomed by rememberSaveable { mutableStateOf(false) }
    var showUI by rememberSaveable { mutableStateOf(true) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }
    var gestureOwner by remember { mutableStateOf(GestureOwner.None) }
    var isImageTransformActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // iOS-style pull-to-dismiss state
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val dismissProgress by remember {
        derivedStateOf {
            (dismissOffsetY.absoluteValue / DISMISS_PROGRESS_DISTANCE).coerceIn(0f, 1f)
        }
    }
    
    // Dismiss animation - complete the dismiss flow with Navigation 3 integration
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            // Animate the dismiss completion before calling onBack
            // This gives NavDisplay time to show the predictive back transition
            val targetOffset = if (dismissOffsetY > 0) DISMISS_COMPLETE_OFFSET else -DISMISS_COMPLETE_OFFSET
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
        showExplanation = false
        dismissOffsetY = 0f
        isDismissing = false
        gestureOwner = GestureOwner.None
        isImageTransformActive = false
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
                .pointerInput(
                    isZoomed,
                    isDismissing,
                    dismissThreshold,
                    dismissVelocityThreshold,
                    isImageTransformActive,
                    gestureOwner,
                ) {
                    awaitEachGesture {
                        val down = awaitFirstDown()

                        // Only start dismiss gesture if not zoomed and starting from center area
                        if (
                            isZoomed ||
                            isDismissing ||
                            isImageTransformActive ||
                            gestureOwner == GestureOwner.ImageTransform
                        ) {
                            return@awaitEachGesture
                        }

                        var dragStarted = false
                        val velocityTracker = VelocityTracker()

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            // Check if vertical movement dominates - lower threshold for easier trigger
                            val totalDeltaY = change.position.y - down.position.y
                            val totalDeltaX = kotlin.math.abs(change.position.x - down.position.x)
                            val absTotalDeltaY = kotlin.math.abs(totalDeltaY)

                            if (!dragStarted && isPredominantlyVerticalDrag(absTotalDeltaY, totalDeltaX)) {
                                dragStarted = true
                                gestureOwner = GestureOwner.Dismiss
                            }

                            if (dragStarted) {
                                // Apply resistance to the drag - compute based on current offset
                                val deltaY = change.position.y - change.previousPosition.y
                                val resistedDelta = applyDismissResistance(
                                    deltaY = deltaY,
                                    dismissOffsetY = dismissOffsetY,
                                    dismissThreshold = dismissThreshold,
                                )

                                dismissOffsetY += resistedDelta
                                velocityTracker.addPointerInputChange(change)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (dragStarted) {
                            val velocity = velocityTracker.calculateVelocity()
                            when (
                                decideDismiss(
                                    dismissOffsetY = dismissOffsetY,
                                    velocityY = velocity.y,
                                    dismissThreshold = dismissThreshold,
                                    dismissVelocityThreshold = dismissVelocityThreshold,
                                )
                            ) {
                                DismissDecision.Dismiss -> {
                                    isDismissing = true
                                    gestureOwner = GestureOwner.None
                                }

                                DismissDecision.Cancel -> {
                                    scope.launch {
                                        animateDismissOffsetBackToRest(initialOffset = dismissOffsetY) {
                                            dismissOffsetY = it
                                        }
                                        gestureOwner = GestureOwner.None
                                    }
                                }
                            }
                        } else if (gestureOwner == GestureOwner.Dismiss) {
                            gestureOwner = GestureOwner.None
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
                        isActivePage = pagerState.currentPage == page,
                        onZoomChanged = {
                            isZoomed = it
                            if (it) showUI = false
                        },
                        onSingleTap = onToggleUI,
                        onTransformGestureActiveChange = { active ->
                            if (pagerState.currentPage == page) {
                                isImageTransformActive = active
                                gestureOwner = when {
                                    active -> GestureOwner.ImageTransform
                                    gestureOwner == GestureOwner.ImageTransform -> GestureOwner.None
                                    else -> gestureOwner
                                }
                            }
                        },
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
                val buttonCount = (if (hasOverlay) 1 else 0) + (if (hasDescription) 1 else 0)
                MultiChoiceSegmentedButtonRow {
                    var buttonIndex = 0
                    if (hasOverlay) {
                        SegmentedButton(
                            checked = showOverlay,
                            onCheckedChange = { showOverlay = it },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = buttonIndex++,
                                count = buttonCount
                            ),
                            icon = {
                                SegmentedButtonDefaults.Icon(showOverlay) {
                                    Icon(
                                        imageVector = if (showOverlay)
                                            Icons.Filled.Visibility
                                        else
                                            Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            label = { Text("Overlay") }
                        )
                    }

                    if (hasDescription) {
                        SegmentedButton(
                            checked = showExplanation,
                            onCheckedChange = { showExplanation = it },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = buttonIndex++,
                                count = buttonCount
                            ),
                            icon = {
                                SegmentedButtonDefaults.Icon(showExplanation) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            label = { Text("Info") }
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
    isActivePage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    onTransformGestureActiveChange: (Boolean) -> Unit,
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
                onZoomChanged = onZoomChanged,
                onSingleTap = onSingleTap,
                onTransformGestureActiveChange = onTransformGestureActiveChange,
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
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    onTransformGestureActiveChange: (Boolean) -> Unit,
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
    val scope = rememberCoroutineScope()

    val isZoomed by remember(scale) {
        derivedStateOf { scale > MIN_SCALE + 0.01f }
    }

    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    DisposableEffect(Unit) {
        onDispose {
            animationJob?.cancel()
            onTransformGestureActiveChange(false)
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
            return Offset(x = maxOf(0f, maxX), y = maxOf(0f, maxY))
        }

        fun clampOffset(x: Float, y: Float, currentScale: Float): Offset {
            if (currentScale <= MIN_SCALE) return Offset.Zero
            val bounds = boundsFor(currentScale)
            return Offset(
                x = x.coerceIn(-bounds.x, bounds.x),
                y = y.coerceIn(-bounds.y, bounds.y),
            )
        }

        fun targetOffsetForDoubleTap(tapOffset: Offset, targetScale: Float): Offset {
            if (targetScale <= MIN_SCALE) return Offset.Zero
            val center = Offset(containerWidth / 2f, containerHeight / 2f)
            val raw = Offset(
                x = (center.x - tapOffset.x) * (targetScale - 1f),
                y = (center.y - tapOffset.y) * (targetScale - 1f),
            )
            return clampOffset(raw.x, raw.y, targetScale)
        }

        val transformModifier = Modifier
            .pointerInput(containerWidth, containerHeight, scale) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var shouldHandleTransform = scale > MIN_SCALE + 0.01f

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (pointerCount > 1) {
                            shouldHandleTransform = true
                        }

                        if (!shouldHandleTransform) {
                            continue
                        }

                        onTransformGestureActiveChange(true)

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid()

                        val previousScale = scale
                        val nextScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                        val center = Offset(containerWidth / 2f, containerHeight / 2f)

                        val baseOffset = Offset(offsetX, offsetY)
                        val relativeToContent = if (previousScale > 0f) {
                            (centroid - center - baseOffset) / previousScale
                        } else {
                            Offset.Zero
                        }

                        val updatedOffset = centroid - center - (relativeToContent * nextScale) + panChange
                        val clampedOffset = clampOffset(
                            x = updatedOffset.x,
                            y = updatedOffset.y,
                            currentScale = nextScale,
                        )

                        scale = nextScale
                        offsetX = clampedOffset.x
                        offsetY = clampedOffset.y

                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })

                    onTransformGestureActiveChange(false)
                }
            }
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

private enum class DismissDecision {
    Dismiss,
    Cancel,
}

private fun isPredominantlyVerticalDrag(absTotalDeltaY: Float, totalDeltaX: Float): Boolean {
    return absTotalDeltaY > DRAG_START_DISTANCE && absTotalDeltaY > totalDeltaX * VERTICAL_DRAG_DOMINANCE
}

private fun applyDismissResistance(deltaY: Float, dismissOffsetY: Float, dismissThreshold: Float): Float {
    val progress = (dismissOffsetY.absoluteValue / dismissThreshold).coerceIn(0f, 2f)
    val resistanceFactor = kotlin.math.cos(progress * kotlin.math.PI / 4.0).toFloat() * 0.7f + 0.3f
    return deltaY * resistanceFactor
}

private fun decideDismiss(
    dismissOffsetY: Float,
    velocityY: Float,
    dismissThreshold: Float,
    dismissVelocityThreshold: Float,
): DismissDecision {
    val absOffset = kotlin.math.abs(dismissOffsetY)
    val absVelocity = kotlin.math.abs(velocityY)
    val shouldDismiss = absOffset > dismissThreshold || absVelocity > dismissVelocityThreshold

    if (!shouldDismiss) {
        return DismissDecision.Cancel
    }

    val velocityMatchesDirection =
        (dismissOffsetY > 0 && velocityY > 0) || (dismissOffsetY < 0 && velocityY < 0)

    return if (absOffset > dismissThreshold * 0.5f || velocityMatchesDirection) {
        DismissDecision.Dismiss
    } else {
        DismissDecision.Cancel
    }
}

private suspend fun animateDismissOffsetBackToRest(
    initialOffset: Float,
    onValueChanged: (Float) -> Unit,
) {
    androidx.compose.animation.core.animate(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
    ) { value, _ ->
        onValueChanged(lerp(start = initialOffset, stop = 0f, fraction = value))
    }
}

private fun getMediaType(fileName: String): MediaType {
    return com.medicalquiz.app.shared.utils.MediaTypeUtils.fromFileName(fileName)
}

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
