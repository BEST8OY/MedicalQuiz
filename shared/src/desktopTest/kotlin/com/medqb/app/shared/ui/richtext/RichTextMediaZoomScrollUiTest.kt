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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import com.medqb.app.shared.ui.LocalActiveSharedElementKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(
    ExperimentalTestApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    DelicateCoilApi::class,
)
class RichTextMediaZoomScrollUiTest {

    @Test
    fun verticalDragOnInlineImageScrollsParentSmoothlyWithoutTriggeringClick() = runComposeUiTest {
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
            lateinit var scrollState: androidx.compose.foundation.ScrollState
            var clickCount = 0

            setContent {
                MaterialExpressiveTheme {
                    scrollState = rememberScrollState()
                    Box(modifier = Modifier.requiredSize(400.dp, 600.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState),
                        ) {
                            Spacer(modifier = Modifier.fillMaxWidth().height(150.dp))
                            RichMedia(
                                block = RichTextBlock.Media(
                                    source = "media://test.png",
                                    mediaRef = "test.png",
                                    description = "inline_target",
                                    width = null,
                                    alignment = TextAlign.Start,
                                ),
                                onMediaClick = { clickCount++ },
                            )
                            Spacer(modifier = Modifier.fillMaxWidth().height(800.dp))
                        }
                    }
                }
            }

            waitForIdle()
            assertEquals(0, scrollState.value)

            onNodeWithContentDescription("inline_target").performTouchInput {
                swipeUp(startY = centerY + 60f, endY = centerY - 120f, durationMillis = 200L)
            }
            waitForIdle()

            assertTrue(scrollState.value > 0, "Expected vertical drag on inline image to scroll parent")
            assertEquals(0, clickCount, "Expected vertical drag not to fire image click")
        } finally {
            SingletonImageLoader.reset()
        }
    }

    @Test
    fun tappingInlineImagePreservesScrollAndStampsDistinctOwnerTokens() = runComposeUiTest {
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
            lateinit var scrollState: androidx.compose.foundation.ScrollState
            val stampedTokens = mutableListOf<String?>()

            setContent {
                MaterialExpressiveTheme {
                    scrollState = rememberScrollState()
                    val activeKeyState = remember { mutableStateOf<String?>(null) }
                    CompositionLocalProvider(LocalActiveSharedElementKey provides activeKeyState) {
                        Box(modifier = Modifier.requiredSize(400.dp, 600.dp)) {
                            RichText(
                                blocks = listOf(
                                    RichTextBlock.Media(
                                        source = "media://test.png",
                                        mediaRef = "test.png",
                                        description = "first_target",
                                        width = 200,
                                        alignment = TextAlign.Start,
                                    ),
                                    RichTextBlock.Media(
                                        source = "media://test.png",
                                        mediaRef = "test.png",
                                        description = "second_target",
                                        width = 200,
                                        alignment = TextAlign.Start,
                                    ),
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                                onMediaClick = { stampedTokens.add(activeKeyState.value) },
                            )
                        }
                    }
                }
            }

            waitForIdle()
            onNodeWithContentDescription("first_target").performClick()
            onNodeWithContentDescription("second_target").performClick()
            waitForIdle()

            assertEquals(2, stampedTokens.size)
            val firstToken = assertNotNull(stampedTokens[0])
            val secondToken = assertNotNull(stampedTokens[1])
            assertTrue(firstToken.startsWith("test.png#"))
            assertTrue(secondToken.startsWith("test.png#"))
            assertNotEquals(firstToken, secondToken, "Expected duplicate media blocks to produce distinct owner tokens")
            assertEquals(0, scrollState.value)
        } finally {
            SingletonImageLoader.reset()
        }
    }
}
