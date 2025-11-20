package com.medicalquiz.app.shared

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder

fun generateImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25)
                .build()
        }
        .crossfade(true)
        .build()
}
