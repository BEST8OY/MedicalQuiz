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
}
