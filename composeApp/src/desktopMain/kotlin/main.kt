package com.medicalquiz.app.shared

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import medicalquiz.composeapp.generated.resources.Res
import medicalquiz.composeapp.generated.resources.app_icon

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MedicalQuiz",
        icon = painterResource(Res.drawable.app_icon)
    ) {
        App()
    }
}

