package com.medicalquiz.app.shared.platform

expect object FolderPicker {
    fun hasPersistedFolder(): Boolean
    fun getPersistedTreeUriString(): String?
    fun saveTreeUri(uriString: String)
}
