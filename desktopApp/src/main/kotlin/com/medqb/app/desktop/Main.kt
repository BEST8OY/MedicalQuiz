package com.medqb.app.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.medqb.app.shared.App
import com.medqb.app.shared.di.DesktopAppGraph
import com.medqb.app.shared.di.LocalAppGraph
import com.medqb.app.shared.generated.resources.Res
import com.medqb.app.shared.generated.resources.app_icon
import dev.zacsweers.metro.createGraph
import org.jetbrains.compose.resources.painterResource

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
