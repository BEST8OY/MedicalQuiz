package com.medqb.app.shared.ui.screens.media

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.adaptive.ProvideAdaptiveLayoutInfo
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HtmlViewerScreenUiTest {

    @Test
    fun wideViewportConstrainsContentWidthAndAppliesRichTextScale() = runComposeUiTest {
        var maxAllowedWidthPx = 0f
        var fontSizeAtLargeScale = 0f

        setContent {
            maxAllowedWidthPx = with(LocalDensity.current) { 840.dp.toPx() }
            ProvideAdaptiveLayoutInfo(modifier = Modifier.requiredSize(1200.dp, 800.dp)) {
                HtmlViewerScreen(
                    fileName = "guideline.html",
                    htmlContent = "<p>Clinical guideline summary for acute coronary syndrome management across wide viewports.</p>",
                    fileExists = true,
                    isLoading = false,
                    richTextScale = 1.3f,
                    onBack = {},
                )
            }
        }

        val textNode = onNodeWithText("Clinical guideline summary", substring = true).assertIsDisplayed()
        val semanticsNode = textNode.fetchSemanticsNode()
        val bounds = semanticsNode.boundsInRoot
        assertTrue(
            bounds.width <= maxAllowedWidthPx + 1f,
            "Expected HTML content width (${bounds.width}px) to be constrained to <= 840.dp (${maxAllowedWidthPx}px)"
        )

        val textLayouts = mutableListOf<TextLayoutResult>()
        semanticsNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(textLayouts)
        fontSizeAtLargeScale = textLayouts.firstOrNull()?.layoutInput?.style?.fontSize?.value ?: 0f
        assertTrue(fontSizeAtLargeScale > 16f, "Expected scaled fontSize (>16sp) for 1.3f richTextScale, got $fontSizeAtLargeScale")
    }
}
