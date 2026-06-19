package com.medqb.app.shared.platform

actual object TextIntentLauncher {
    actual fun openSelectedText(text: String): Boolean {
        return false
    }
}
