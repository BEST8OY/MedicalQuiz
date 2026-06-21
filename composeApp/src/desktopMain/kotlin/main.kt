package com.medqb.app.shared

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.zacsweers.metro.createGraph
import org.jetbrains.compose.resources.painterResource
import medqb.composeapp.generated.resources.Res
import medqb.composeapp.generated.resources.app_icon
import com.medqb.app.shared.di.DesktopAppGraph
import com.medqb.app.shared.di.LocalAppGraph

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MedQB",
        icon = painterResource(Res.drawable.app_icon)
    ) {
        val graph = createGraph<DesktopAppGraph>()
        CompositionLocalProvider(LocalAppGraph provides graph) {
            App()
        }
    }
}

