package com.medqb.app.shared.ui.screens.media

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import com.medqb.app.shared.ui.LocalActiveSharedElementKey
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.richtext.RichMedia
import com.medqb.app.shared.ui.richtext.RichTextBlock
import kotlinx.serialization.Serializable
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.SnapBackZoomableOverlayHost
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalZoomableApi::class)
class MediaSharedElementTransitionUiTest {

    private sealed interface TestRoute : NavKey {
        @Serializable
        data object Source : TestRoute

        @Serializable
        data class Viewer(val file: String) : TestRoute
    }

    @Composable
    private fun TestSharedElementNavHost(
        backStack: MutableList<NavKey>,
        sourceContainerModifier: Modifier,
        thumbDescription: String?,
        thumbWidth: Int?,
        onMediaHandlerReady: (MediaHandler) -> Unit,
        onScopeReady: (SharedTransitionScope) -> Unit = {},
    ) {
        MaterialExpressiveTheme {
            SharedTransitionLayout {
                onScopeReady(this)
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
                    SnapBackZoomableOverlayHost(modifier = Modifier.fillMaxSize()) {
                        NavDisplay(
                            backStack = backStack,
                            onBack = { if (backStack.size > 1) backStack.removeLast() },
                            entryDecorators = listOf(saveableDecorator),
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                            entryProvider = entryProvider {
                                entry<TestRoute.Source> {
                                    val activeKey = rememberSaveable { mutableStateOf<String?>(null) }
                                    val handler = remember(activeKey) {
                                        MediaHandler(
                                            onOpenMedia = { files, _ ->
                                                backStack.add(TestRoute.Viewer(files.first()))
                                            },
                                            onOpenHtml = {},
                                            activeSharedElementKey = activeKey,
                                        ).apply {
                                            updateMedia(1L, listOf("test.png"))
                                        }
                                    }
                                    onMediaHandlerReady(handler)
                                    CompositionLocalProvider(LocalActiveSharedElementKey provides handler.activeSharedElementKey) {
                                        Box(
                                            modifier = sourceContainerModifier,
                                            contentAlignment = Alignment.TopStart,
                                        ) {
                                            RichMedia(
                                                block = RichTextBlock.Media(
                                                    source = "media://test.png",
                                                    mediaRef = "test.png",
                                                    description = thumbDescription,
                                                    width = thumbWidth,
                                                    alignment = TextAlign.Start,
                                                ),
                                                onMediaClick = { ref -> handler.handleMediaLink(ref) },
                                            )
                                        }
                                    }
                                }
                                entry<TestRoute.Viewer> { key ->
                                    MediaContent(
                                        fileName = key.file,
                                        isActivePage = true,
                                        isSharedElementPage = true,
                                        resolveMediaFilePath = { it },
                                        mediaFileExists = { true },
                                        onZoomChanged = {},
                                        onSingleTap = {},
                                        sharedTransitionScope = LocalSharedTransitionScope.current,
                                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun returningAfterEntranceSettlesStillActivatesSharedElementTransition() = runComposeUiTest {
        val backStack = mutableStateListOf<NavKey>(TestRoute.Source)
        lateinit var mediaHandler: MediaHandler
        var scopeRef: SharedTransitionScope? = null
        var observedTransitionActiveDuringPop = false

        setContent {
            TestSharedElementNavHost(
                backStack = backStack,
                sourceContainerModifier = Modifier.size(120.dp),
                thumbDescription = null,
                thumbWidth = null,
                onMediaHandlerReady = { mediaHandler = it },
                onScopeReady = { scopeRef = it },
            )
        }

        waitForIdle()

        // Navigate to Viewer and advance past the entrance transition so Source leaves composition.
        runOnUiThread {
            mediaHandler.handleMediaLink("test.png")
        }
        mainClock.autoAdvance = false
        repeat(40) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }

        // Now pop back after the entrance transition has fully settled.
        runOnUiThread {
            backStack.removeLast()
        }
        repeat(15) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
            if (scopeRef?.isTransitionActive == true) {
                observedTransitionActiveDuringPop = true
            }
        }

        assertTrue(
            observedTransitionActiveDuringPop,
            "Expected SharedTransitionScope to activate shared element shrink-back transition on pop after entrance settled",
        )
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    fun popTransitionStartsAtCenteredImageBoundsInsteadOfTopOfScreen() = runComposeUiTest {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(
                    Fetcher.Factory<Any> { _, _, _ ->
                        Fetcher {
                            ImageFetchResult(
                                image = ColorImage(color = 0xFF336699.toInt(), width = 400, height = 225),
                                isSampled = false,
                                dataSource = DataSource.MEMORY,
                            )
                        }
                    }
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(loader)
        try {
            val backStack = mutableStateListOf<NavKey>(TestRoute.Source)
            lateinit var mediaHandler: MediaHandler

            setContent {
                Box(modifier = Modifier.requiredSize(400.dp, 600.dp)) {
                    TestSharedElementNavHost(
                        backStack = backStack,
                        sourceContainerModifier = Modifier
                            .fillMaxSize()
                            .padding(top = 400.dp),
                        thumbDescription = "thumb",
                        thumbWidth = 200,
                        onMediaHandlerReady = { mediaHandler = it },
                    )
                }
            }

            waitForIdle()
            runOnUiThread { mediaHandler.handleMediaLink("test.png") }
            waitForIdle()

            mainClock.autoAdvance = false
            repeat(40) {
                mainClock.advanceTimeByFrame()
                waitForIdle()
            }

            // In a 400x600 viewport, a 400x225 (16:9) image is centered vertically at ~187.5.dp.
            val viewerTopBeforePop = onNodeWithContentDescription("test.png").getUnclippedBoundsInRoot().top
            assertTrue(
                viewerTopBeforePop > 150.dp,
                "Expected MediaContent fitted shared element to be centered vertically, but top was $viewerTopBeforePop",
            )

            runOnUiThread { backStack.removeLast() }
            waitForIdle()
            var observedThumbDuringPop = false
            repeat(8) { frame ->
                mainClock.advanceTimeByFrame()
                waitForIdle()
                val nodes = onAllNodesWithContentDescription("thumb", useUnmergedTree = true).fetchSemanticsNodes()
                if (nodes.isNotEmpty()) {
                    observedThumbDuringPop = true
                    val thumbTopPx = nodes.first().boundsInRoot.top
                    val viewerTopPx = onAllNodesWithContentDescription("test.png", useUnmergedTree = true)
                        .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
                    assertTrue(
                        thumbTopPx > 150f,
                        "Expected returning shared element on frame $frame to stay centered/animating toward thumbnail (> 150px, viewer=$viewerTopPx), not jump to top ($thumbTopPx)",
                    )
                }
            }
            assertTrue(observedThumbDuringPop, "Expected returning thumb node to be observed during pop transition")
        } finally {
            SingletonImageLoader.reset()
        }
    }
}
