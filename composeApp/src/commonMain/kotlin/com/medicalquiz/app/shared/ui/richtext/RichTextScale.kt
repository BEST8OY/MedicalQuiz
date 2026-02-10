package com.medicalquiz.app.shared.ui.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit

private const val TABLE_SCALE_FACTOR = 0.9f

@Immutable
internal data class RichTextScale(
    val proseScale: Float = 1f,
) {
    val tableScale: Float
        get() = proseScale * TABLE_SCALE_FACTOR
}

internal val LocalRichTextScale = compositionLocalOf { RichTextScale() }

@Composable
fun RichTextScaleProvider(
    proseScale: Float,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRichTextScale provides RichTextScale(proseScale = proseScale),
        content = content,
    )
}

internal fun TextStyle.scaledBy(scale: Float): TextStyle {
    val resolvedScale = scale.coerceAtLeast(0.5f)
    return copy(
        fontSize = fontSize.scaleIfSpecified(resolvedScale),
        lineHeight = lineHeight.scaleIfSpecified(resolvedScale),
    )
}

private fun TextUnit.scaleIfSpecified(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * scale
