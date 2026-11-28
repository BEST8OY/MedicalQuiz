package com.medqb.app.shared.ui.screens.media

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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
import com.medqb.app.shared.ui.media.MediaAspectRatioCache
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.richtext.RichMedia
import com.medqb.app.shared.ui.richtext.RichText
import com.medqb.app.shared.ui.richtext.RichTextBlock
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
class MediaSharedElementTransitionUiTest {

    @BeforeTest
    fun setUp() {
        MediaAspectRatioCache.clear()
    }

    @AfterTest
    fun tearDown() {
        MediaAspectRatioCache.clear()
    }

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
        onScopeReady: (SharedTransitionScope) -> Unit = {},
        onDensityReady: (Density) -> Unit = {},
    ) {
        onDensityReady(LocalDensity.current)
        MaterialExpressiveTheme {
            SharedTransitionLayout {
                onScopeReady(this)
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
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
                                            onMediaClick = { ref, ownerKey -> handler.handleMediaLink(ref, ownerKey) },
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

    @Test
    fun returningAfterEntranceSettlesStillActivatesSharedElementTransition() = runComposeUiTest {
        val backStack = mutableStateListOf<NavKey>(TestRoute.Source)
        var scopeRef: SharedTransitionScope? = null
        var observedTransitionActiveDuringPop = false

        setContent {
            TestSharedElementNavHost(
                backStack = backStack,
                sourceContainerModifier = Modifier.size(120.dp),
                thumbDescription = "thumb",
                thumbWidth = null,
                onScopeReady = { scopeRef = it },
            )
        }

        waitForIdle()
        onNodeWithContentDescription("thumb").performClick()

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
            lateinit var density: Density

            setContent {
                Box(modifier = Modifier.requiredSize(400.dp, 600.dp)) {
                    TestSharedElementNavHost(
                        backStack = backStack,
                        sourceContainerModifier = Modifier
                            .fillMaxSize()
                            .padding(top = 400.dp),
                        thumbDescription = "thumb",
                        thumbWidth = 200,
                        onDensityReady = { density = it },
                    )
                }
            }

            waitForIdle()
            onNodeWithContentDescription("thumb").performClick()
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

            val minCenteredTopPx = with(density) { 150.dp.toPx() }
            val maxCenteredTopPx = with(density) { 250.dp.toPx() }
            val minViewerWidthPx = with(density) { 300.dp.toPx() }

            runOnUiThread { backStack.removeLast() }
            waitForIdle()
            var observedThumbDuringPop = false
            repeat(8) { frame ->
                mainClock.advanceTimeByFrame()
                waitForIdle()
                val nodes = onAllNodesWithContentDescription("thumb", useUnmergedTree = true).fetchSemanticsNodes()
                if (nodes.isNotEmpty()) {
                    observedThumbDuringPop = true
                    val bounds = nodes.first().boundsInRoot
                    val thumbTopPx = bounds.top
                    val viewerTopPx = onAllNodesWithContentDescription("test.png", useUnmergedTree = true)
                        .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
                    assertTrue(
                        thumbTopPx > minCenteredTopPx,
                        "Expected returning shared element on frame $frame to stay centered/animating toward thumbnail (> $minCenteredTopPx, viewer=$viewerTopPx), not jump to top ($thumbTopPx)",
                    )
                    if (frame <= 1) {
                        assertTrue(
                            bounds.width > minViewerWidthPx && bounds.top < maxCenteredTopPx,
                            "Expected returning shared element on frame $frame to start at centered viewer bounds (~0,187.5dp,400dp,412.5dp), not jump to $bounds",
                        )
                    }
                }
            }
            assertTrue(observedThumbDuringPop, "Expected returning thumb node to be observed during pop transition")
        } finally {
            SingletonImageLoader.reset()
        }
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    fun duplicateImagesRespectTappedOwnerOnReturnPopTransition() = runComposeUiTest {
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
                    },
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(loader)
        try {
            val backStack = mutableStateListOf<NavKey>(TestRoute.Source)
            lateinit var density: Density

            setContent {
                density = LocalDensity.current
                Box(modifier = Modifier.requiredSize(400.dp, 600.dp)) {
                    MaterialExpressiveTheme {
                        SharedTransitionLayout {
                            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                                val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
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
                                            CompositionLocalProvider(
                                                LocalActiveSharedElementKey provides handler.activeSharedElementKey,
                                            ) {
                                                RichText(
                                                    blocks = listOf(
                                                        RichTextBlock.Media(
                                                            source = "media://test.png",
                                                            mediaRef = "test.png",
                                                            description = "thumb_first",
                                                            width = 200,
                                                            alignment = TextAlign.Start,
                                                        ),
                                                        RichTextBlock.Media(
                                                            source = "media://test.png",
                                                            mediaRef = "test.png",
                                                            description = "thumb_second",
                                                            width = 200,
                                                            alignment = TextAlign.Start,
                                                        ),
                                                    ),
                                                    mediaOwnerPrefix = "stem",
                                                    modifier = Modifier.fillMaxSize(),
                                                    onMediaWithOwnerClick = { ref, ownerKey ->
                                                        handler.handleMediaLink(ref, ownerKey)
                                                    },
                                                )
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

            waitForIdle()
            onNodeWithContentDescription("thumb_second").performClick()
            waitForIdle()

            mainClock.autoAdvance = false
            repeat(40) {
                mainClock.advanceTimeByFrame()
                waitForIdle()
            }

            val minCenteredTopPx = with(density) { 150.dp.toPx() }
            val maxCenteredTopPx = with(density) { 250.dp.toPx() }
            val minViewerWidthPx = with(density) { 300.dp.toPx() }
            val maxThumbWidthPx = with(density) { 220.dp.toPx() }

            runOnUiThread { backStack.removeLast() }
            waitForIdle()

            var observedSecondAtViewerStart = false
            repeat(8) { frame ->
                mainClock.advanceTimeByFrame()
                waitForIdle()
                val secondNodes = onAllNodesWithContentDescription("thumb_second", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                val firstNodes = onAllNodesWithContentDescription("thumb_first", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                if (secondNodes.isNotEmpty() && frame <= 1) {
                    val secondBounds = secondNodes.first().boundsInRoot
                    observedSecondAtViewerStart = secondBounds.width > minViewerWidthPx &&
                        secondBounds.top in minCenteredTopPx..maxCenteredTopPx
                    assertTrue(
                        observedSecondAtViewerStart,
                        "Expected tapped second duplicate image to own frame $frame return bounds, got $secondBounds",
                    )
                    if (firstNodes.isNotEmpty()) {
                        val firstBounds = firstNodes.first().boundsInRoot
                        assertTrue(
                            firstBounds.width <= maxThumbWidthPx && firstBounds.top < minCenteredTopPx,
                            "Expected first duplicate image to stay in its rest slot, got $firstBounds",
                        )
                    }
                }
            }
            assertTrue(
                observedSecondAtViewerStart,
                "Expected tapped second duplicate image to start return transition from centered viewer bounds",
            )
        } finally {
            SingletonImageLoader.reset()
        }
    }

    @Test
    fun layoutBranchSwitchWhileViewingMediaPreservesReturnSharedElement() = runComposeUiTest {
        val backStack = mutableStateListOf<NavKey>(TestRoute.Source)
        var scopeRef: SharedTransitionScope? = null
        var useTwoPane by mutableStateOf(false)
        var observedTransitionActiveDuringPop = false

        setContent {
            MaterialExpressiveTheme {
                SharedTransitionLayout {
                    scopeRef = this
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
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
                                    CompositionLocalProvider(
                                        LocalActiveSharedElementKey provides handler.activeSharedElementKey,
                                    ) {
                                        val content = @Composable {
                                            RichText(
                                                blocks = listOf(
                                                    RichTextBlock.Media(
                                                        source = "media://test.png",
                                                        mediaRef = "test.png",
                                                        description = "thumb",
                                                        width = 160,
                                                        alignment = TextAlign.Start,
                                                    ),
                                                ),
                                                mediaOwnerPrefix = "stem",
                                                onMediaWithOwnerClick = { ref, ownerKey ->
                                                    handler.handleMediaLink(ref, ownerKey)
                                                },
                                            )
                                        }
                                        if (useTwoPane) {
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                Box(modifier = Modifier.weight(1f)) { content() }
                                                Box(modifier = Modifier.weight(1f))
                                            }
                                        } else {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                content()
                                            }
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

        waitForIdle()
        onNodeWithContentDescription("thumb").performClick()

        mainClock.autoAdvance = false
        repeat(40) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }

        // Simulate portrait -> landscape orientation change while viewing MediaViewer.
        runOnUiThread {
            useTwoPane = true
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
            "Expected semantic owner key to preserve return shared-element transition across layout branch switch",
        )
    }
}
