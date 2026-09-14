package com.medqb.app.shared.ui.screens.media

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.platform.PlatformBackHandler
import com.medqb.app.shared.ui.media.MediaAnchorRegistry
import com.medqb.app.shared.ui.media.MediaOverlayState
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Option B: In-place Fullscreen Media Viewer Overlay Host.
 *
 * Renders directly inside the root [Box] of [App] above [NavDisplay].
 * Enables continuous single-gesture dismissal (drag-down / pinch-to-dismiss)
 * directly from arbitrary zoom states into the origin thumbnail with zero
 * duplicate image ghosting and zero frame-0 disappearance.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaViewerOverlayHost(
    overlayState: MediaOverlayState,
    anchorRegistry: MediaAnchorRegistry,
    mediaDescriptions: Map<String, MediaDescription> = emptyMap(),
    richTextScale: Float = 1f,
    resolveMediaFilePath: (String) -> String,
    mediaFileExists: suspend (String) -> Boolean,
    resolveOverlayPaths: suspend (List<String>) -> Map<String, String?>,
    onLinkClick: ((String) -> Unit)? = null,
    onSaveMedia: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (!overlayState.isVisible || overlayState.mediaFiles.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeIndex by remember(overlayState.mediaFiles) { mutableIntStateOf(overlayState.initialIndex) }
    var isZoomed by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var isReadyToRender by remember { mutableStateOf(false) }

    val motionScheme = MaterialTheme.motionScheme
    val slowSpatialSpec = motionScheme.slowSpatialSpec<Float>()
    val defaultSpatialSpec = motionScheme.defaultSpatialSpec<Float>()
    val defaultEffectsSpec = motionScheme.defaultEffectsSpec<Float>()
    val fastEffectsSpec = motionScheme.fastEffectsSpec<Float>()

    val animTranslationX = remember { Animatable(0f) }
    val animTranslationY = remember { Animatable(0f) }
    val animScale = remember { Animatable(1f) }
    val animCorner = remember { Animatable(0f) }
    val scrimAlpha = remember { Animatable(0f) }
    val chromeAlpha = remember { Animatable(0f) }

    val currentFileName = overlayState.mediaFiles.getOrNull(activeIndex) ?: ""

    fun computeAnchorTransforms(anchor: Rect?, size: IntSize): Triple<Offset, Float, Float> {
        val screenW = size.width.toFloat().coerceAtLeast(1f)
        val screenH = size.height.toFloat().coerceAtLeast(1f)
        val screenCenterX = screenW / 2f
        val screenCenterY = screenH / 2f

        return if (anchor != null && anchor.width > 0 && anchor.height > 0) {
            val deltaX = (anchor.left + anchor.width / 2f) - screenCenterX
            val deltaY = (anchor.top + anchor.height / 2f) - screenCenterY
            val targetScale = (anchor.width / screenW).coerceIn(0.05f, 1f)
            Triple(Offset(deltaX, deltaY), targetScale, 12f)
        } else {
            Triple(Offset(0f, 120f), 0.85f, 0f)
        }
    }

    // Opening animation from thumbnail anchor to fullscreen
    LaunchedEffect(overlayState.isVisible, containerSize) {
        if (overlayState.isVisible && containerSize.width > 0 && containerSize.height > 0) {
            val initialFile = overlayState.mediaFiles.getOrNull(overlayState.initialIndex) ?: ""
            val initialAnchor = anchorRegistry.getBounds(initialFile)
            val (initialDelta, initialScale, initialCorner) = computeAnchorTransforms(initialAnchor, containerSize)

            animTranslationX.snapTo(initialDelta.x)
            animTranslationY.snapTo(initialDelta.y)
            animScale.snapTo(initialScale)
            animCorner.snapTo(initialCorner)
            scrimAlpha.snapTo(0f)
            chromeAlpha.snapTo(0f)
            isReadyToRender = true

            kotlinx.coroutines.joinAll(
                launch { animTranslationX.animateTo(0f, slowSpatialSpec) },
                launch { animTranslationY.animateTo(0f, slowSpatialSpec) },
                launch { animScale.animateTo(1f, slowSpatialSpec) },
                launch { animCorner.animateTo(0f, slowSpatialSpec) },
                launch { scrimAlpha.animateTo(1f, defaultEffectsSpec) },
                launch { chromeAlpha.animateTo(1f, defaultEffectsSpec) },
            )
        }
    }

    val triggerDismiss: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            val currentAnchor = anchorRegistry.getBounds(currentFileName)
            val (destDelta, destScale, destCorner) = computeAnchorTransforms(currentAnchor, containerSize)

            coroutineScope.launch {
                try {
                    kotlinx.coroutines.joinAll(
                        launch { animTranslationX.animateTo(destDelta.x, defaultSpatialSpec) },
                        launch { animTranslationY.animateTo(destDelta.y, defaultSpatialSpec) },
                        launch { animScale.animateTo(destScale, defaultSpatialSpec) },
                        launch { animCorner.animateTo(destCorner, defaultSpatialSpec) },
                        launch { scrimAlpha.animateTo(0f, fastEffectsSpec) },
                        launch { chromeAlpha.animateTo(0f, fastEffectsSpec) },
                    )
                } finally {
                    overlayState.close()
                    isDismissing = false
                    isReadyToRender = false
                }
            }
        }
    }

    PlatformBackHandler(enabled = overlayState.isVisible && !isDismissing) {
        triggerDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .onSizeChanged { containerSize = it },
    ) {
        // Scrim backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha.value }
                .background(Color.Black.copy(alpha = 0.95f)),
        )

        // Single persistent media viewer surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (isReadyToRender) 1f else 0f
                    translationX = animTranslationX.value
                    translationY = animTranslationY.value
                    scaleX = animScale.value
                    scaleY = animScale.value
                    clip = animCorner.value > 0.5f
                    shape = RoundedCornerShape(animCorner.value.dp)
                }
                .pointerInput(isZoomed, isDismissing, containerSize) {
                    if (!isZoomed && !isDismissing && containerSize.height > 0) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (animTranslationY.value.absoluteValue > 120f) {
                                    triggerDismiss()
                                } else {
                                    // Spring back to centered fullscreen
                                    coroutineScope.launch {
                                        kotlinx.coroutines.joinAll(
                                            launch { animTranslationX.animateTo(0f, defaultSpatialSpec) },
                                            launch { animTranslationY.animateTo(0f, defaultSpatialSpec) },
                                            launch { animScale.animateTo(1f, defaultSpatialSpec) },
                                            launch { animCorner.animateTo(0f, defaultSpatialSpec) },
                                            launch { scrimAlpha.animateTo(1f, defaultEffectsSpec) },
                                            launch { chromeAlpha.animateTo(1f, defaultEffectsSpec) },
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    kotlinx.coroutines.joinAll(
                                        launch { animTranslationX.animateTo(0f, defaultSpatialSpec) },
                                        launch { animTranslationY.animateTo(0f, defaultSpatialSpec) },
                                        launch { animScale.animateTo(1f, defaultSpatialSpec) },
                                        launch { animCorner.animateTo(0f, defaultSpatialSpec) },
                                        launch { scrimAlpha.animateTo(1f, defaultEffectsSpec) },
                                        launch { chromeAlpha.animateTo(1f, defaultEffectsSpec) },
                                    )
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val newY = animTranslationY.value + dragAmount
                                val newX = animTranslationX.value + change.positionChange().x * 0.3f
                                val dismissProgress = (newY.absoluteValue / (containerSize.height * 0.35f)).coerceIn(0f, 1f)

                                coroutineScope.launch {
                                    animTranslationY.snapTo(newY)
                                    animTranslationX.snapTo(newX)
                                    animScale.snapTo(1f - dismissProgress * 0.25f)
                                    animCorner.snapTo(dismissProgress * 12f)
                                    scrimAlpha.snapTo((1f - dismissProgress * 0.85f).coerceIn(0f, 1f))
                                    chromeAlpha.snapTo((1f - dismissProgress * 3f).coerceIn(0f, 1f))
                                }
                            },
                        )
                    }
                },
        ) {
            MediaViewerScreen(
                mediaFiles = overlayState.mediaFiles,
                startIndex = overlayState.initialIndex,
                mediaDescriptions = mediaDescriptions,
                richTextScale = richTextScale,
                resolveMediaFilePath = resolveMediaFilePath,
                mediaFileExists = mediaFileExists,
                resolveOverlayPaths = resolveOverlayPaths,
                onLinkClick = onLinkClick,
                onBack = { triggerDismiss() },
                onSaveMedia = onSaveMedia,
                onCurrentIndexChanged = { activeIndex = it },
                onZoomStateChanged = { isZoomed = it },
                controlsAlpha = chromeAlpha.value,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
