package com.medqb.app.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.medqb.app.shared.App
import com.medqb.app.shared.di.DesktopAppGraph
import com.medqb.app.shared.di.LocalAppGraph
import com.medqb.app.shared.generated.resources.Res
import com.medqb.app.shared.generated.resources.app_icon
import dev.zacsweers.metro.createGraph
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 780.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "MedQB",
        icon = painterResource(Res.drawable.app_icon)
    ) {
        LaunchedEffect(window) {
            window.minimumSize = Dimension(420, 480)
        }
        val graph = remember { createGraph<DesktopAppGraph>() }
        CompositionLocalProvider(LocalAppGraph provides graph) {
            App()
        }
    }
}
