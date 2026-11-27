package com.medqb.app.shared.ui.richtext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import kotlinx.coroutines.launch
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.SnapBackZoomableOverlayHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(
    ExperimentalTestApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalZoomableApi::class,
    DelicateCoilApi::class,
)
class RichTextMediaZoomScrollUiTest {

    @Test
    fun pinchZoomReleaseDoesNotCauseScrollPositionJump() = runComposeUiTest {
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
            lateinit var scrollState: androidx.compose.foundation.ScrollState
            lateinit var scope: kotlinx.coroutines.CoroutineScope

            setContent {
                MaterialExpressiveTheme {
                    scrollState = rememberScrollState()
                    scope = rememberCoroutineScope()
                    Box(modifier = Modifier.requiredSize(400.dp, 600.dp)) {
                        SnapBackZoomableOverlayHost(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                            ) {
                                Spacer(modifier = Modifier.fillMaxWidth().height(250.dp))
                                RichMedia(
                                    block = RichTextBlock.Media(
                                        source = "media://test.png",
                                        mediaRef = "test.png",
                                        description = "zoom_target",
                                        width = null,
                                        alignment = TextAlign.Start,
                                    ),
                                    onMediaClick = {},
                                )
                                Spacer(modifier = Modifier.fillMaxWidth().height(600.dp))
                            }
                        }
                    }
                }
            }

            waitForIdle()
            runOnUiThread {
                scope.launch {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }
            waitForIdle()

            val initialScroll = scrollState.value
            assertTrue(initialScroll > 0, "Expected scrollState to be scrolled to bottom, was $initialScroll")

            // Perform a pinch-out zoom and release on the inline thumbnail.
            onNodeWithContentDescription("zoom_target").performTouchInput {
                val cx = center.x
                val cy = center.y
                pinch(
                    start0 = Offset(cx - 20f, cy),
                    end0 = Offset(cx - 100f, cy),
                    start1 = Offset(cx + 20f, cy),
                    end1 = Offset(cx + 100f, cy),
                    durationMillis = 200L,
                )
            }

            mainClock.autoAdvance = false
            repeat(30) { frame ->
                mainClock.advanceTimeByFrame()
                waitForIdle()
                assertEquals(
                    initialScroll,
                    scrollState.value,
                    "Expected scroll position to remain at $initialScroll on frame $frame after pinch release",
                )
            }
        } finally {
            SingletonImageLoader.reset()
        }
    }
}
