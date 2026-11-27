package com.medqb.app.shared.ui.screens.media

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
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

    @Test
    fun returningAfterEntranceSettlesStillActivatesSharedElementTransition() = runComposeUiTest {
        val backStack = mutableStateListOf<NavKey>(TestRoute.Source)
        lateinit var mediaHandler: MediaHandler
        var scopeRef: SharedTransitionScope? = null
        var observedTransitionActiveDuringPop = false

        setContent {
            MaterialExpressiveTheme {
                SharedTransitionLayout {
                    scopeRef = this
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
                                        mediaHandler = remember(activeKey) {
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
                                        CompositionLocalProvider(LocalActiveSharedElementKey provides mediaHandler.activeSharedElementKey) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                                                Box(modifier = Modifier.size(120.dp)) {
                                                    RichMedia(
                                                        block = RichTextBlock.Media(
                                                            source = "media://test.png",
                                                            mediaRef = "test.png",
                                                            description = null,
                                                            width = null,
                                                            alignment = TextAlign.Start,
                                                        ),
                                                        onMediaClick = { ref -> mediaHandler.handleMediaLink(ref) },
                                                    )
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
}
