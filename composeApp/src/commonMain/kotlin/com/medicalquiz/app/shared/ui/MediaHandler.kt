package com.medicalquiz.app.shared.ui

class MediaHandler(
    private val onOpenMedia: (List<String>, Int) -> Unit,
    private val onOpenHtml: (String) -> Unit
) {
    private var currentQuestionId: Long? = null
    private var currentMediaFiles: List<String> = emptyList()

    fun reset() {
        currentQuestionId = null
        currentMediaFiles = emptyList()
    }

    fun updateMedia(questionId: Long, mediaFiles: List<String>) {
        currentQuestionId = questionId
        currentMediaFiles = mediaFiles
    }

    fun handleMediaLink(url: String): Boolean {
        if (url.startsWith("media://")) {
            val fileName = url.substringAfter("media://")
            return openMediaFromCache(fileName)
        }

        if (url.startsWith("file://") && url.contains("/media/")) {
            val fileName = url.substringAfterLast('/')
            return openMediaFromCache(fileName)
        }

        if (url.startsWith("media/", ignoreCase = true)) {
            val fileName = url.substringAfterLast('/')
            return openMediaFromCache(fileName)
        }

        // Handle HTML files separately in standalone viewer
        if (url.endsWith(".html", ignoreCase = true) || url.endsWith(".htm", ignoreCase = true)) {
            val fileName = url.substringAfterLast('/')
            onOpenHtml(fileName)
            return true
        }

        if (url.endsWith(".jpg", ignoreCase = true) || 
            url.endsWith(".jpeg", ignoreCase = true) || 
            url.endsWith(".png", ignoreCase = true) ||
            url.endsWith(".gif", ignoreCase = true) ||
            url.endsWith(".webp", ignoreCase = true) ||
            url.endsWith(".mp4", ignoreCase = true) ||
            url.endsWith(".mp3", ignoreCase = true)) {
            val fileName = url.substringAfterLast('/')
            return openMediaFromCache(fileName)
        }

        if (!url.contains("/") && !url.startsWith("http") && !url.startsWith("file://")) {
            // Check if it's an HTML file without path
            if (url.contains(".html", ignoreCase = true) || url.contains(".htm", ignoreCase = true)) {
                onOpenHtml(url)
                return true
            }
            return openMediaFromCache(url)
        }

        return false
    }

    fun showCurrentMediaGallery(startIndex: Int = 0): Boolean = openMediaFromCache(null, startIndex)

    private fun openMediaFromCache(fileName: String?, fallbackIndex: Int = 0): Boolean {
        if (currentMediaFiles.isEmpty()) return false
        val startIndex = resolveStartIndex(currentMediaFiles, fileName, fallbackIndex)
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
