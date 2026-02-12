package com.medicalquiz.app.shared.platform

actual object TextIntentLauncher {
    actual fun openSelectedText(text: String): Boolean {
        return false
    }
}
