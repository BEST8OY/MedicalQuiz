package com.medicalquiz.app.shared.platform

actual object FolderPicker {
    actual fun hasPersistedFolder(): Boolean = false
    actual fun getPersistedTreeUriString(): String? = null
    actual fun saveTreeUri(uriString: String) { }
}
