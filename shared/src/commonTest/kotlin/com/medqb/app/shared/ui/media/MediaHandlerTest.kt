package com.medqb.app.shared.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaHandlerTest {

    @Test
    fun externalHttpAndHttpsLinksReturnFalseAndAreNotIntercepted() {
        var openedMedia: List<String>? = null
        var openedHtml: String? = null

        val handler = MediaHandler(
            onOpenMedia = { files, _ -> openedMedia = files },
            onOpenHtml = { file -> openedHtml = file }
        )
        handler.updateMedia(1L, listOf("chart.png", "guideline.html"))

        assertFalse(handler.handleMediaLink("https://example.com/guideline.html"))
        assertFalse(handler.handleMediaLink("http://example.com/chart.png"))
        assertFalse(handler.handleMediaLink("https://example.com/page"))
        assertFalse(handler.handleMediaLink("http://example.com/video.mp4"))
        assertFalse(handler.handleMediaLink("HTTPS://EXAMPLE.COM/guideline.HTML?v=1"))
        assertFalse(handler.handleMediaLink("HTTP://EXAMPLE.COM/chart.PNG#section"))

        assertEquals(null, openedMedia)
        assertEquals(null, openedHtml)
    }

    @Test
    fun localMediaAndHtmlLinksAreHandledCorrectly() {
        var openedMedia: List<String>? = null
        var openedHtml: String? = null

        val handler = MediaHandler(
            onOpenMedia = { files, _ -> openedMedia = files },
            onOpenHtml = { file -> openedHtml = file }
        )
        handler.updateMedia(1L, listOf("image1.png", "image2.jpg"))

        assertTrue(handler.handleMediaLink("media://image1.png"))
        assertEquals(listOf("image1.png", "image2.jpg"), openedMedia)

        assertTrue(handler.handleMediaLink("document.html"))
        assertEquals("document.html", openedHtml)
    }

    @Test
    fun textLinkClearsOwnerKeyAndBlockedNavigationDoesNotMutateSharedElementKey() {
        var canNav = true
        var openedCount = 0
        val handler = MediaHandler(
            onOpenMedia = { _, _ -> openedCount++ },
            onOpenHtml = {},
            canNavigate = { canNav },
        )
        handler.updateMedia(1L, listOf("image1.png"))

        assertTrue(handler.handleMediaLink("image1.png", sharedElementOwnerKey = "stem:0:image1.png"))
        assertEquals("stem:0:image1.png", handler.activeSharedElementKey.value)
        assertEquals(1, openedCount)

        // When navigation is blocked (e.g., not RESUMED), activeSharedElementKey must not change.
        canNav = false
        assertFalse(handler.handleMediaLink("image1.png", sharedElementOwnerKey = "stem:1:image1.png"))
        assertEquals("stem:0:image1.png", handler.activeSharedElementKey.value)
        assertEquals(1, openedCount)

        // Clicking a text link (no owner key) clears any prior inline thumbnail owner key.
        canNav = true
        assertTrue(handler.handleMediaLink("media://image1.png"))
        assertEquals(null, handler.activeSharedElementKey.value)
        assertEquals(2, openedCount)
    }
}
