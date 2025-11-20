package com.medicalquiz.app.shared

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.setSingletonImageLoaderFactory

fun main() = application {
    setSingletonImageLoaderFactory { context ->
        generateImageLoader(context)
    }

    Window(onCloseRequest = ::exitApplication, title = "MedicalQuiz") {
        App()
    }
}
