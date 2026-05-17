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

    fun handleMediaLink(url: String): Boolean {
        val fileName = extractFileName(url) ?: return false

        return when {
            MediaTypeUtils.isHtml(fileName) -> {
                onOpenHtml(fileName)
                true
            }
            MediaTypeUtils.isMediaFile(fileName) -> openMedia(fileName)
            else -> false
        }
    }

    fun showCurrentMediaGallery(startIndex: Int = 0): Boolean = openMedia(startIndex = startIndex)

    private fun openMedia(fileName: String? = null, startIndex: Int = 0): Boolean {
        if (currentMediaFiles.isEmpty()) return false

        val resolvedIndex = fileName?.let(::findMediaIndex)
            ?: startIndex.coerceIn(0, currentMediaFiles.lastIndex)
        if (resolvedIndex < 0) return false

        onOpenMedia(currentMediaFiles, resolvedIndex)
        return true
    }

    private fun findMediaIndex(fileName: String): Int = currentMediaFiles.indexOfFirst { mediaFile ->
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
