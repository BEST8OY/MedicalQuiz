package com.medicalquiz.app.shared

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.compose.LocalImageLoader
import coil3.compose.LocalPlatformContext

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "MedicalQuiz") {
        val context = LocalPlatformContext.current
        val imageLoader = remember(context) {
            generateImageLoader(context)
        }

        CompositionLocalProvider(LocalImageLoader provides imageLoader) {
            App()
        }
    }
}
