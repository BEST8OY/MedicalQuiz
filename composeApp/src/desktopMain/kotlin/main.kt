package com.medqb.app.shared

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import medqb.composeapp.generated.resources.Res
import medqb.composeapp.generated.resources.app_icon

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MedQB",
        icon = painterResource(Res.drawable.app_icon)
    ) {
        App()
    }
}

