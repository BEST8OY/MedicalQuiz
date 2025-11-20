package com.medicalquiz.app.shared

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.compose.LocalImageLoader
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "MedicalQuiz") {
        val context = LocalPlatformContext.current
        val imageLoader = remember(context) {
            ImageLoader.Builder(context)
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

        CompositionLocalProvider(LocalImageLoader provides imageLoader) {
            App()
        }
    }
}
