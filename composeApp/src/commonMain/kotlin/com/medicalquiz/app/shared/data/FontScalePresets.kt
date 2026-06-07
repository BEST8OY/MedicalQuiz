package com.medicalquiz.app.shared.data

import kotlin.math.abs

object FontScalePresets {
    const val COMPACT = 0.9f
    const val DEFAULT = 1f
    const val LARGE = 1.15f
    const val EXTRA_LARGE = 1.3f

    val all = listOf(COMPACT, DEFAULT, LARGE, EXTRA_LARGE)

    fun nearestTo(scale: Float): Float =
        all.minBy { option -> abs(option - scale) }
}
