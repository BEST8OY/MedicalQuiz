package com.medqb.app.shared.ui.media

import androidx.compose.runtime.mutableStateOf
import com.medqb.app.shared.utils.HtmlUtils

class MediaHandler(
    private val onOpenMedia: (List<String>, Int) -> Unit,
    private val onOpenHtml: (String) -> Unit
) {
    private var currentQuestionId: Long? = null
    private var currentMediaFiles: List<String> = emptyList()

    val activeSharedElementKey = mutableStateOf<String?>(null)

    fun reset() {
        currentQuestionId = null
        currentMediaFiles = emptyList()
        activeSharedElementKey.value = null
    }

    fun updateMedia(questionId: Long, mediaFiles: List<String>) {
        currentQuestionId = questionId
        currentMediaFiles = mediaFiles
    }

    fun handleMediaLink(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false

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
            // Check if it's an HTML file without path
            if (trimmed.contains(".html", ignoreCase = true) || trimmed.contains(".htm", ignoreCase = true)) {
                onOpenHtml(trimmed)
                return true
            }
            val fileName = extractedFileName(trimmed) ?: trimmed
            return openMediaFromCache(fileName)
        }

        return false
    }

    fun showCurrentMediaGallery(startIndex: Int = 0): Boolean = openMediaFromCache(null, startIndex)

    private fun openMediaFromCache(fileName: String?, fallbackIndex: Int = 0): Boolean {
        if (currentMediaFiles.isEmpty()) return false

        if (fileName != null) {
            val matchingIndex = currentMediaFiles.indexOfFirst { it.equals(fileName, ignoreCase = true) }
            if (matchingIndex < 0) {
                // Don't open an unrelated item when the clicked filename isn't in the current cache.
                return false
            }
            activeSharedElementKey.value = fileName
            onOpenMedia(currentMediaFiles, matchingIndex)
            return true
        }

        val startIndex = resolveStartIndex(currentMediaFiles, null, fallbackIndex)
        onOpenMedia(currentMediaFiles, startIndex)
        return true
    }

    private fun resolveStartIndex(mediaFiles: List<String>, fileName: String?, fallbackIndex: Int): Int {
        if (mediaFiles.size == 1) return 0
        val matchingIndex = fileName?.let { target ->
            mediaFiles.indexOfFirst { it.equals(target, ignoreCase = true) }
        }
        return matchingIndex?.takeIf { it >= 0 }
            ?: fallbackIndex.coerceIn(0, mediaFiles.lastIndex)
    }
}
