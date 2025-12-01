package com.medicalquiz.app.shared

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import okio.Path.Companion.toPath
import com.medicalquiz.app.shared.platform.StorageProvider

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
        .diskCache {
            DiskCache.Builder()
                .directory("${StorageProvider.getAppStorageDirectory()}/image_cache".toPath())
                .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                .build()
        }
        .crossfade(true)
        .build()
}