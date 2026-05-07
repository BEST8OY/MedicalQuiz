package com.medicalquiz.app.shared.ui.richtext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RichTextMediaTest {

    @Test
    fun `mediaAspectRatioFor prefers valid html dimensions`() {
        assertEquals(
            expected = 2f,
            actual = mediaAspectRatioFor(
                width = 640,
                height = 320,
                intrinsicWidth = 100f,
                intrinsicHeight = 100f,
            ),
        )
    }

    @Test
    fun `mediaAspectRatioFor falls back to decoded intrinsic dimensions`() {
        assertEquals(
            expected = 1.5f,
            actual = mediaAspectRatioFor(
                width = null,
                height = null,
                intrinsicWidth = 900f,
                intrinsicHeight = 600f,
            ),
        )
    }

    @Test
    fun `mediaAspectRatioFor ignores missing and invalid dimensions`() {
        assertNull(
            mediaAspectRatioFor(
                width = 0,
                height = 320,
                intrinsicWidth = Float.NaN,
                intrinsicHeight = 600f,
            ),
        )
        assertNull(
            mediaAspectRatioFor(
                width = null,
                height = null,
                intrinsicWidth = Float.POSITIVE_INFINITY,
                intrinsicHeight = 600f,
            ),
        )
    }
}
