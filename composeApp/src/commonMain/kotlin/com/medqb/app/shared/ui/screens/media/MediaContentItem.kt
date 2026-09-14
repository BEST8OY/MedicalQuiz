package com.medqb.app.shared.ui.screens.media

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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.medqb.app.shared.ui.media.MediaType
import com.medqb.app.shared.ui.theme.ContainerSize
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.utils.MediaTypeUtils
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MIN_SCALE = 1f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaContent(
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
    modifier: Modifier = Modifier,
) {
    val mediaType = remember(fileName) { getMediaType(fileName) }
    val filePath = remember(fileName, resolveMediaFilePath) { resolveMediaFilePath(fileName) }

    val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val defaultSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fastSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    AnimatedContent(
        targetState = mediaType,
        modifier = modifier,
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
                isActivePage = isActivePage,
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
    val fileExists by produceState(initialValue = true, fileName, filePath) {
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
    val fileExists by produceState(initialValue = true, fileName, filePath) {
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
    isActivePage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    overlayPath: String? = null,
    showOverlay: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val fileExists by produceState(initialValue = true, fileName, mediaFilePath) {
        value = mediaFileExists(fileName)
    }

    if (!fileExists) {
        UnsupportedContent(fileName = fileName)
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val zoomState = rememberZoomState()
    val isZoomed = zoomState.scale > MIN_SCALE + 0.01f

    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    val motionScheme = MaterialTheme.motionScheme
    val defaultSpatialFloatSpec = motionScheme.defaultSpatialSpec<Float>()
    val fastSpatialFloatSpec = motionScheme.fastSpatialSpec<Float>()
    val slowSpatialSpec = motionScheme.slowSpatialSpec<androidx.compose.ui.geometry.Rect>()
    val fastSpatialSpec = motionScheme.fastSpatialSpec<androidx.compose.ui.geometry.Rect>()

    // Uses sharedElement with Material 3 Expressive motionScheme for pure single-element
    // hero transition without duplicate image crossfade artifacts.
    val sharedElementModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "media_$fileName"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { initialBounds, targetBounds ->
                    val isExpanding = initialBounds.width * initialBounds.height < targetBounds.width * targetBounds.height
                    if (isExpanding) slowSpatialSpec else fastSpatialSpec
                },
                clipInOverlayDuringTransition = OverlayClip(RectangleShape),
            )
        }
    } else Modifier

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val center = remember(containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            Offset(containerSize.width / 2f, containerSize.height / 2f)
        } else {
            Offset.Zero
        }
    }

    // Option A: Two-stage back gesture handling (Standard Mobile UX).
    // When the user is zoomed into an image (inspection mode), intercepting the system back
    // gesture smoothly resets the zoom back to MIN_SCALE (1.0x) rather than popping the screen.
    // Once reset to MIN_SCALE, isZoomed becomes false, disabling this back handler so that
    // the subsequent back gesture pops the screen and runs the 1.0x -> thumbnail shared element
    // transition cleanly without visual snapping or duplicate image ghosting.
    //
    // FUTURE REFACTOR (Option B - Single-Surface Overlay):
    // If a seamless single-gesture dismissal directly from an arbitrary zoomed state is desired
    // (similar to Telegram / Google Photos drag-down dismiss), MediaViewer cannot remain a separate
    // NavDisplay backstack route connecting two independent composables. Compose's Modifier.sharedElement
    // hardcodes renderOnlyWhenVisible = true (instantly rendering the unzoomed target thumbnail on exit),
    // and Modifier.sharedBounds crossfades the two differing scales (producing duplicate image ghosting).
    // Option B would refactor MediaViewer into an in-place fullscreen overlay within the same
    // screen hierarchy, allowing a single persistent image surface and its 2D transformation matrix
    // to be continuously interpolated from (scale, offset) to destination thumbnail bounds.
    PlatformBackHandler(enabled = isActivePage && isZoomed) {
        coroutineScope.launch {
            zoomState.changeScale(
                targetScale = MIN_SCALE,
                position = center,
                animationSpec = fastSpatialFloatSpec,
            )
        }
    }

    val isExitingTransition = animatedVisibilityScope?.transition?.targetState?.let {
        it == EnterExitState.PostExit || it == EnterExitState.PreEnter
    } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .then(sharedElementModifier)
            .clipToBounds()
            .zoomable(
                zoomState = zoomState,
                onDoubleTap = { position ->
                    val targetScale = if (zoomState.scale < 2f) DOUBLE_TAP_ZOOM else MIN_SCALE
                    zoomState.changeScale(targetScale, position, defaultSpatialFloatSpec)
                },
                onTap = { onSingleTap() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        var isTransitionDone by remember { mutableStateOf(animatedVisibilityScope == null) }

        LaunchedEffect(animatedVisibilityScope) {
            if (animatedVisibilityScope != null) {
                snapshotFlow { animatedVisibilityScope.transition.isRunning }
                    .collect { running ->
                        if (!running) {
                            isTransitionDone = true
                        }
                    }
            }
        }

        AsyncImage(
            model = mediaFilePath,
            contentDescription = fileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    zoomState.setContentSize(state.painter.intrinsicSize)
                }
            },
        )

        AnimatedVisibility(
            visible = overlayPath != null && showOverlay && !isExitingTransition && isTransitionDone,
            enter = fadeIn(
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            ),
            exit = fadeOut(
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            ),
        ) {
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
internal fun UnsupportedContent(
    fileName: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.ExtraLarge),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(ContainerSize.ExtraLarge),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ContainerSize.Medium),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.Medium))
            Text(
                text = "Unsupported Media",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.Small))
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
