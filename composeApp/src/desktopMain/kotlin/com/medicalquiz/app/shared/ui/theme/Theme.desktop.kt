package com.medicalquiz.app.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
actual fun getPlatformColorScheme(darkTheme: Boolean): ColorScheme? {
    return null
}

internal fun isDesktopDarkTheme(): Boolean = runBlocking {
    withContext(Dispatchers.IO) {
        try {
            val systemProp = System.getProperty("compose.desktop.dark.mode")?.toBoolean()
            if (systemProp != null) return@withContext systemProp

            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("linux") -> checkGnomeDarkMode()
                os.contains("mac") || os.contains("os x") -> checkMacDarkMode()
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}

private fun checkGnomeDarkMode(): Boolean {
    val proc = Runtime.getRuntime().exec(arrayOf(
        "gsettings", "get", "org.gnome.desktop.interface", "color-scheme"
    ))
    return proc.inputStream.bufferedReader().use { reader ->
        proc.waitFor()
        reader.readText().trim().contains("dark", ignoreCase = true)
    }
}

private fun checkMacDarkMode(): Boolean {
    val proc = Runtime.getRuntime().exec(arrayOf(
        "defaults", "read", "-g", "AppleInterfaceStyle"
    ))
    return proc.inputStream.bufferedReader().use { reader ->
        proc.waitFor()
        reader.readText().trim().contains("dark", ignoreCase = true)
    }
}
