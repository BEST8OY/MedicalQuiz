package com.medqb.app.shared.ui.screens.media

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import com.medqb.app.shared.ui.components.SnackbarBottomClearanceEffect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaViewerScreen(
    mediaFiles: List<String>,
    startIndex: Int = 0,
    mediaDescriptions: Map<String, MediaDescription> = emptyMap(),
    richTextScale: Float = 1f,
    resolveMediaFilePath: (String) -> String,
    mediaFileExists: suspend (String) -> Boolean,
    resolveOverlayPaths: suspend (List<String>) -> Map<String, String?>,
    onLinkClick: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    onSaveMedia: ((String) -> Unit)? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val navAnimatedScope = animatedVisibilityScope ?: if (sharedTransitionScope != null) {
        LocalNavAnimatedContentScope.current
    } else null

    MediaViewerContent(
        mediaFiles = mediaFiles,
        startIndex = startIndex,
        mediaDescriptions = mediaDescriptions,
        richTextScale = richTextScale,
        resolveMediaFilePath = resolveMediaFilePath,
        mediaFileExists = mediaFileExists,
        resolveOverlayPaths = resolveOverlayPaths,
        onLinkClick = onLinkClick,
        onBack = onBack,
        onSaveMedia = onSaveMedia,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = navAnimatedScope,
        modifier = modifier,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaViewerContent(
    mediaFiles: List<String>,
    startIndex: Int,
    mediaDescriptions: Map<String, MediaDescription>,
    richTextScale: Float,
    resolveMediaFilePath: (String) -> String,
    mediaFileExists: suspend (String) -> Boolean,
    resolveOverlayPaths: suspend (List<String>) -> Map<String, String?>,
    onLinkClick: ((String) -> Unit)?,
    onBack: () -> Unit,
    onSaveMedia: ((String) -> Unit)?,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    if (mediaFiles.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            UnsupportedContent(fileName = "No media")
        }
        return
    }

    val safeInitialPage = startIndex.coerceIn(0, mediaFiles.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { mediaFiles.size },
    )
    var isZoomed by remember { mutableStateOf(false) }
    var resetZoomTrigger by remember { mutableIntStateOf(0) }
    var showUI by rememberSaveable { mutableStateOf(true) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }
    val currentFileName = mediaFiles.getOrNull(pagerState.currentPage) ?: ""
    val currentDescription = mediaDescriptions[currentFileName]

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect {
                isZoomed = false
                showOverlay = true
                showExplanation = false
            }
    }

    val onToggleUI: () -> Unit = remember { { showUI = !showUI } }

    val overlayPathsByFile by produceState<Map<String, String?>>(initialValue = emptyMap(), mediaFiles) {
        value = resolveOverlayPaths(mediaFiles)
    }
    val currentOverlayPath = overlayPathsByFile[currentFileName]
    val hasOverlay = currentOverlayPath != null
    val hasDescription = currentDescription != null
    val controlsLayout = remember(hasOverlay, hasDescription) {
        resolveControlsLayout(hasOverlay = hasOverlay, hasDescription = hasDescription)
    }

    SnackbarBottomClearanceEffect(
        clearance = 88.dp,
        active = showUI && controlsLayout != MediaControlsLayout.None,
    )

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (showUI) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceDim,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (showExplanation || event.type != KeyEventType.KeyDown ||
                    event.isCtrlPressed || event.isMetaPressed || event.isAltPressed
                ) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> if (!isZoomed && pagerState.currentPage > 0) {
                        val targetPage = pagerState.currentPage - 1
                        scope.launch { pagerState.animateScrollToPage(targetPage) }
                        true
                    } else {
                        false
                    }
                    Key.DirectionRight -> if (!isZoomed && pagerState.currentPage < mediaFiles.lastIndex) {
                        val targetPage = pagerState.currentPage + 1
                        scope.launch { pagerState.animateScrollToPage(targetPage) }
                        true
                    } else {
                        false
                    }
                    Key.Escape -> {
                        if (isZoomed) {
                            resetZoomTrigger++
                        } else {
                            onBack()
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            key = { index -> "$index:${mediaFiles[index]}" },
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isZoomed,
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        ) { page ->
            val maxState = if (pagerState.settledPage == page) Lifecycle.State.RESUMED else Lifecycle.State.STARTED
            val pageLifecycleOwner = rememberLifecycleOwner(maxLifecycle = maxState)

            CompositionLocalProvider(LocalLifecycleOwner provides pageLifecycleOwner) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            val fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                            alpha = lerp(0.5f, 1f, fraction)
                            val scale = lerp(0.85f, 1f, fraction)
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    MediaContent(
                        fileName = mediaFiles[page],
                        isActivePage = pagerState.currentPage == page,
                        isSharedElementPage = page == safeInitialPage && page == pagerState.currentPage,
                        resetZoomTrigger = resetZoomTrigger,
                        resolveMediaFilePath = resolveMediaFilePath,
                        mediaFileExists = mediaFileExists,
                        onZoomChanged = {
                            if (pagerState.currentPage == page) {
                                isZoomed = it
                                if (it) showUI = false
                            }
                        },
                        onSingleTap = onToggleUI,
                        overlayPath = overlayPathsByFile[mediaFiles[page]],
                        showOverlay = showOverlay,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }

        MediaViewerTopBar(
            showUI = showUI,
            mediaFilesCount = mediaFiles.size,
            pagerState = pagerState,
            currentFileName = currentFileName,
            onBack = onBack,
            onSaveMedia = onSaveMedia,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        MediaViewerBottomBar(
            showUI = showUI,
            controlsLayout = controlsLayout,
            showOverlay = showOverlay,
            onShowOverlayChange = { showOverlay = it },
            onShowInfo = { showExplanation = true },
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (showExplanation && currentDescription != null) {
            ExplanationBottomSheet(
                description = currentDescription,
                richTextScale = richTextScale,
                onDismiss = { showExplanation = false },
                onLinkClick = onLinkClick,
            )
        }
    }
}
