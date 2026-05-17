package com.medicalquiz.app.shared.ui.media

import com.medicalquiz.app.shared.utils.HtmlUtils
import com.medicalquiz.app.shared.utils.MediaTypeUtils

class MediaHandler(
    private val onOpenMedia: (List<String>, Int) -> Unit,
    private val onOpenHtml: (String) -> Unit
) {
    private var currentMediaFiles: List<String> = emptyList()

    fun reset() {
        currentMediaFiles = emptyList()
    }

    fun updateMedia(mediaFiles: List<String>) {
        currentMediaFiles = mediaFiles
    }

    fun handleMediaLink(url: String, mediaFiles: List<String> = currentMediaFiles): Boolean {
        val fileName = extractFileName(url) ?: return false

        if (MediaTypeUtils.isHtml(fileName)) {
            onOpenHtml(fileName)
            return true
        }

        if (MediaTypeUtils.isMediaFile(fileName)) {
            val mediaOpened = openMedia(mediaFiles, fileName = fileName)
            if (mediaOpened) return true
        }

        return false
    }

    fun showCurrentMediaGallery(startIndex: Int = 0): Boolean = openMedia(currentMediaFiles, startIndex = startIndex)

    private fun openMedia(mediaFiles: List<String>, fileName: String? = null, startIndex: Int = 0): Boolean {
        if (mediaFiles.isEmpty()) return false

        val resolvedIndex = fileName?.let { findMediaIndex(mediaFiles, it) }
            ?: startIndex.coerceIn(0, mediaFiles.lastIndex)
        if (resolvedIndex < 0) return false

        onOpenMedia(mediaFiles, resolvedIndex)
        return true
    }

    private fun findMediaIndex(mediaFiles: List<String>, fileName: String): Int = mediaFiles.indexOfFirst { mediaFile ->
        HtmlUtils.normalizeFileName(mediaFile).equals(fileName, ignoreCase = true)
    }

    private fun extractFileName(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null

        val source = if (trimmed.startsWith(MEDIA_SCHEME, ignoreCase = true)) {
            trimmed.drop(MEDIA_SCHEME.length)
        } else {
            trimmed
        }
        return HtmlUtils.normalizeFileName(source).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val MEDIA_SCHEME = "media://"
    }
}
