package com.medqb.app.shared.platform

/**
 * Opens selected text in an external app.
 *
 * Android uses system app resolution so users can choose an app and optionally set it as default.
 */
expect object TextIntentLauncher {
    fun openSelectedText(text: String): Boolean
}
