package com.medqb.app.shared.ui.screens.media

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.toggleScale
import net.engawapg.lib.zoomable.zoomable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.produceState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import com.medqb.app.shared.ui.media.MediaType
import com.medqb.app.shared.utils.MediaTypeUtils
import com.medqb.app.shared.ui.richtext.RichText
import com.medqb.app.shared.ui.richtext.RichTextScaleProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.medqb.app.shared.ui.theme.ElementSize
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// Animation and interaction constants
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MIN_SCALE = 1f

private enum class MediaControlsLayout {
    None,
    OverlayOnly,
    InfoOnly,
    OverlayAndInfo,
}

private fun resolveControlsLayout(hasOverlay: Boolean, hasDescription: Boolean): MediaControlsLayout = when {
    hasOverlay && hasDescription -> MediaControlsLayout.OverlayAndInfo
    hasOverlay -> MediaControlsLayout.OverlayOnly
    hasDescription -> MediaControlsLayout.InfoOnly
    else -> MediaControlsLayout.None
}

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

    // Only intercept back when explanation bottom sheet is open
    // Otherwise let NavDisplay handle predictive back gesture
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

        val transitionAlpha = animatedVisibilityScope?.transition?.animateFloat(
            transitionSpec = { MaterialTheme.motionScheme.defaultEffectsSpec() },
            label = "chromeAlpha"
        ) { state ->
            when (state) {
                EnterExitState.PreEnter -> 0f
                EnterExitState.Visible -> 1f
                EnterExitState.PostExit -> 0f
            }
        }?.value ?: 1f

        AnimatedVisibility(
            visible = showUI,
            enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = transitionAlpha },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = Spacing.Xs, vertical = Spacing.Xs),
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
                            modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (onSaveMedia != null) {
                    FilledIconButton(
                        onClick = { onSaveMedia(currentFileName) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Save media",
                        )
                    }
                }
            }
        }

        val hasOverlay by derivedStateOf { currentOverlayPath != null }
        val hasDescription = currentDescription != null
        val controlsLayout = remember(hasOverlay, hasDescription) {
            resolveControlsLayout(hasOverlay = hasOverlay, hasDescription = hasDescription)
        }
        val hasControls = controlsLayout != MediaControlsLayout.None
        val controlsWidth = Layout.PanelWidth
        val controlsEnterEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val controlsExitEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

        AnimatedVisibility(
            visible = showUI && hasControls,
            enter = fadeIn(animationSpec = controlsEnterEffects) +
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight / 3 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            exit = fadeOut(animationSpec = controlsExitEffects) +
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight / 3 },
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Spacing.Lg)
                .graphicsLayer { alpha = transitionAlpha },
        ) {
            Box(
                modifier = Modifier.width(controlsWidth),
                contentAlignment = Alignment.Center,
            ) {
                MediaViewerControlButtonGroup(
                    type = controlsLayout,
                    showOverlay = showOverlay,
                    onShowOverlayChange = { showOverlay = it },
                    onShowInfo = { showExplanation = true },
                )
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaViewerControlButtonGroup(
    type: MediaControlsLayout,
    showOverlay: Boolean,
    onShowOverlayChange: (Boolean) -> Unit,
    onShowInfo: () -> Unit,
) {
    val groupModifier = when (type) {
        MediaControlsLayout.OverlayAndInfo -> Modifier.fillMaxWidth()
        MediaControlsLayout.OverlayOnly,
        MediaControlsLayout.InfoOnly,
        MediaControlsLayout.None -> Modifier.width(Layout.PanelWidth)
    }
    val groupArrangement = if (type == MediaControlsLayout.OverlayAndInfo) {
        Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    } else {
        ButtonGroupDefaults.HorizontalArrangement
    }

    ButtonGroup(
        modifier = groupModifier,
        overflowIndicator = {
            ButtonGroupDefaults.OverflowIndicator(menuState = it)
        },
        horizontalArrangement = groupArrangement,
        expandedRatio = ButtonGroupDefaults.ExpandedRatio,
    ) {
        if (type == MediaControlsLayout.OverlayOnly || type == MediaControlsLayout.OverlayAndInfo) {
            toggleableItem(
                checked = showOverlay,
                label = "Overlay",
                onCheckedChange = onShowOverlayChange,
                weight = 1.1f,
                icon = {
                    Icon(
                        imageVector = if (showOverlay) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (showOverlay) "Hide overlay" else "Show overlay",
                    )
                },
            )
        }
        if (type == MediaControlsLayout.InfoOnly || type == MediaControlsLayout.OverlayAndInfo) {
            clickableItem(
                label = "Info",
                onClick = onShowInfo,
                weight = 1.0f,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExplanationBottomSheet(
    description: MediaDescription,
    richTextScale: Float,
    onDismiss: () -> Unit,
    onLinkClick: ((String) -> Unit)?,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Inset.Lg)
                .padding(bottom = Spacing.Xl),
        ) {
            Text(
                text = description.title.ifBlank { "Explanation" },
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.Md),
            )

            HorizontalDivider(
                modifier = Modifier.padding(bottom = Spacing.Md),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            RichTextScaleProvider(proseScale = richTextScale) {
                RichText(
                    html = description.description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaContent(
    fileName: String,
    isActivePage: Boolean,
    isSharedElementPage: Boolean = false,
    resolveMediaFilePath: (String) -> String,
    mediaFileExists: suspend (String) -> Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    overlayPath: String? = null,
    showOverlay: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val mediaType = remember(fileName) { getMediaType(fileName) }
    val filePath = remember(fileName, resolveMediaFilePath) { resolveMediaFilePath(fileName) }

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
                mediaFilePath = filePath,
                mediaFileExists = mediaFileExists,
                onZoomChanged = onZoomChanged,
                onSingleTap = onSingleTap,
                overlayPath = overlayPath,
                showOverlay = showOverlay,
                sharedTransitionScope = if (isSharedElementPage) sharedTransitionScope else null,
                animatedVisibilityScope = if (isSharedElementPage) animatedVisibilityScope else null,
            )
            MediaType.VIDEO -> VideoContent(
                filePath = filePath,
                fileName = fileName,
                mediaFileExists = mediaFileExists,
                isActivePage = isActivePage
            )
            MediaType.AUDIO -> AudioContent(
                filePath = filePath,
                fileName = fileName,
                mediaFileExists = mediaFileExists,
                isActivePage = isActivePage
            )
            else -> UnsupportedContent(fileName = fileName)
        }
    }
}

@Composable
private fun VideoContent(
    filePath: String,
    fileName: String,
    mediaFileExists: suspend (String) -> Boolean,
    isActivePage: Boolean,
) {
    val fileExists by produceState(initialValue = false, filePath) {
        value = mediaFileExists(fileName)
    }

    if (!fileExists) {
        UnsupportedContent(fileName = fileName)
        return
    }

    VideoPlayer(
        filePath = filePath,
        modifier = Modifier.fillMaxSize(),
        isActivePage = isActivePage
    )
}

@Composable
private fun AudioContent(
    filePath: String,
    fileName: String,
    mediaFileExists: suspend (String) -> Boolean,
    isActivePage: Boolean,
) {
    val fileExists by produceState(initialValue = false, filePath) {
        value = mediaFileExists(fileName)
    }

    if (!fileExists) {
        UnsupportedContent(fileName = fileName)
        return
    }

    AudioPlayer(
        filePath = filePath,
        modifier = Modifier.fillMaxSize(),
        isActivePage = isActivePage
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ImageContent(
    fileName: String,
    mediaFilePath: String,
    mediaFileExists: suspend (String) -> Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    overlayPath: String? = null,
    showOverlay: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val filePath = remember(mediaFilePath) { mediaFilePath }

    val fileExists by produceState(initialValue = false, filePath) {
        value = mediaFileExists(fileName)
    }

    if (!fileExists) {
        UnsupportedContent(fileName = fileName)
        return
    }

    val zoomState = rememberZoomState()

    LaunchedEffect(zoomState.scale) {
        onZoomChanged(zoomState.scale > MIN_SCALE + 0.01f)
    }

    val sharedElementModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "media_$fileName"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    val isExitingTransition = animatedVisibilityScope?.transition?.targetState?.let {
        it == EnterExitState.PostExit || it == EnterExitState.PreEnter
    } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zoomable(
                zoomState = zoomState,
                onDoubleTap = { position ->
                    val targetScale = if (zoomState.scale < 2f) DOUBLE_TAP_ZOOM else MIN_SCALE
                    zoomState.changeScale(targetScale, position)
                },
                onTap = { onSingleTap() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        var isLoading by remember { mutableStateOf(true) }

        AsyncImage(
            model = filePath,
            contentDescription = fileName,
            modifier = Modifier.fillMaxSize().then(sharedElementModifier),
            contentScale = ContentScale.Fit,
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
                if (state is AsyncImagePainter.State.Success) {
                    zoomState.setContentSize(state.painter.intrinsicSize)
                }
            },
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        }

        if (overlayPath != null && showOverlay && !isExitingTransition) {
            AsyncImage(
                model = overlayPath,
                contentDescription = "Overlay",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun UnsupportedContent(fileName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.Xl),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(ElementSize.IconContainerXl),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ElementSize.IconContainerMd),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.Md))
            Text(
                text = "Unsupported Media",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.Xs))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun getMediaType(fileName: String): MediaType {
    return MediaTypeUtils.fromFileName(fileName)
}

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
