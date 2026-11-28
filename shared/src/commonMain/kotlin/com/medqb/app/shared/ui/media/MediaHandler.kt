package com.medqb.app.shared.ui.media

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.medqb.app.shared.utils.HtmlUtils

class MediaHandler(
    private val onOpenMedia: (List<String>, Int) -> Unit,
    private val onOpenHtml: (String) -> Unit,
    val activeSharedElementKey: MutableState<String?> = mutableStateOf(null),
) {
    private var currentMediaFiles: List<String> = emptyList()

    fun reset() {
        currentMediaFiles = emptyList()
        activeSharedElementKey.value = null
    }

    fun updateMedia(@Suppress("UNUSED_PARAMETER") questionId: Long, mediaFiles: List<String>) {
        currentMediaFiles = mediaFiles
    }

    fun handleMediaLink(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return false
        }

        // Prefer a consistent filename extraction (drops query/fragment, trims whitespace).
        fun extractedFileName(raw: String): String? {
            val cleaned = raw.substringBefore('?').substringBefore('#').trim()
            if (cleaned.isEmpty()) return null
            val lastSegment = cleaned.substringAfterLast('/')
            return HtmlUtils.normalizeFileName(lastSegment).takeIf { it.isNotBlank() }
        }

        if (trimmed.startsWith("media://")) {
            val fileName = extractedFileName(trimmed.substringAfter("media://"))
            return fileName?.let { openMediaFromCache(it) } ?: false
        }

        if (trimmed.startsWith("file://") && trimmed.contains("/media/")) {
            val fileName = extractedFileName(trimmed)
            return fileName?.let { openMediaFromCache(it) } ?: false
        }

        if (trimmed.startsWith("media/", ignoreCase = true)) {
            val fileName = extractedFileName(trimmed)
            return fileName?.let { openMediaFromCache(it) } ?: false
        }

        // Handle HTML files separately in standalone viewer
        if (trimmed.contains(".html", ignoreCase = true) || trimmed.contains(".htm", ignoreCase = true)) {
            val fileName = extractedFileName(trimmed) ?: trimmed
            if (fileName.isNotBlank()) {
                onOpenHtml(fileName)
                return true
            }
        }

        // Check if it's a media file using centralized MediaTypeUtils
        val extractedName = extractedFileName(trimmed)
        if (extractedName != null && com.medqb.app.shared.utils.MediaTypeUtils.isMediaFile(extractedName)) {
            return openMediaFromCache(extractedName)
        }

        if (!trimmed.contains("/") && !trimmed.startsWith("http") && !trimmed.startsWith("file://")) {
            val fileName = extractedFileName(trimmed) ?: trimmed
            return openMediaFromCache(fileName)
        }

        return false
    }

    private fun openMediaFromCache(fileName: String): Boolean {
        if (activeSharedElementKey.value?.startsWith("$fileName#") != true) {
            activeSharedElementKey.value = fileName
        }
        if (currentMediaFiles.isEmpty()) {
            onOpenMedia(listOf(fileName), 0)
            return true
        }

        val matchingIndex = currentMediaFiles.indexOfFirst { it.equals(fileName, ignoreCase = true) }
        if (matchingIndex < 0) {
            // If requested filename is not in current cached gallery, fallback to opening single requested media
            onOpenMedia(listOf(fileName), 0)
            return true
        }
        onOpenMedia(currentMediaFiles, matchingIndex)
        return true
    }
}
