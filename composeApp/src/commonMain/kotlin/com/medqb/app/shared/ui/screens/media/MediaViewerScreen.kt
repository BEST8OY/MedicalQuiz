package com.medqb.app.shared.ui.screens.media

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.ui.LocalSharedTransitionScope
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
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current

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
        animatedVisibilityScope = animatedVisibilityScope,
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
) {
    if (mediaFiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.systemBars),
            contentAlignment = Alignment.Center,
        ) {
            UnsupportedContent(fileName = "No media")
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { mediaFiles.size },
    )
    var isZoomed by rememberSaveable { mutableStateOf(false) }
    var showUI by rememberSaveable { mutableStateOf(true) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var showOverlay by rememberSaveable { mutableStateOf(true) }
    val currentFileName = mediaFiles.getOrNull(pagerState.currentPage) ?: ""
    val currentDescription = mediaDescriptions[currentFileName]

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        showOverlay = true
        showExplanation = false
    }

    PlatformBackHandler(enabled = showExplanation, onBack = { showExplanation = false })

    val onToggleUI: () -> Unit = { showUI = !showUI }

    val overlayPathsByFile by produceState<Map<String, String?>>(initialValue = emptyMap(), mediaFiles) {
        value = resolveOverlayPaths(mediaFiles)
    }
    val currentOverlayPath = overlayPathsByFile[currentFileName]

    val backgroundColor by animateColorAsState(
        targetValue = if (showUI) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceDim,
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
            val parentLifecycleOwner = LocalLifecycleOwner.current
            val pageLifecycleOwner = remember(parentLifecycleOwner, pagerState.settledPage, page) {
                val maxState = if (pagerState.settledPage == page) Lifecycle.State.RESUMED else Lifecycle.State.STARTED
                object : androidx.lifecycle.LifecycleOwner {
                    private val registry = androidx.lifecycle.LifecycleRegistry(this)
                    private val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
                        val targetState = if (parentLifecycleOwner.lifecycle.currentState < maxState) {
                            parentLifecycleOwner.lifecycle.currentState
                        } else {
                            maxState
                        }
                        registry.currentState = targetState
                    }
                    init {
                        parentLifecycleOwner.lifecycle.addObserver(observer)
                    }
                    override val lifecycle: Lifecycle get() = registry
                }
            }

            CompositionLocalProvider(LocalLifecycleOwner provides pageLifecycleOwner) {
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
                        isSharedElementPage = page == startIndex && page == pagerState.currentPage,
                        resolveMediaFilePath = resolveMediaFilePath,
                        mediaFileExists = mediaFileExists,
                        onZoomChanged = {
                            isZoomed = it
                            if (it) showUI = false
                        },
                        onSingleTap = onToggleUI,
                        overlayPath = if (page == pagerState.currentPage) currentOverlayPath else null,
                        showOverlay = if (page == pagerState.currentPage) showOverlay else true,
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

        val hasOverlay by derivedStateOf { currentOverlayPath != null }
        val hasDescription = currentDescription != null
        val controlsLayout = remember(hasOverlay, hasDescription) {
            resolveControlsLayout(hasOverlay = hasOverlay, hasDescription = hasDescription)
        }

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

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
