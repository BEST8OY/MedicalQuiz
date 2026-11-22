package com.medqb.app.shared

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.medqb.app.shared.platform.StorageProvider
import okio.Path.Companion.toPath

fun generateImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder()
                // Lower cache percent to reduce cold-start heap pressure
                .maxSizePercent(context, 0.10)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(StorageProvider.getImageCacheDirectory().toPath())
                .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                .build()
        }
        .crossfade(true)
        .build()
}
