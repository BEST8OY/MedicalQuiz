package com.medicalquiz.app.shared.ui

class MediaHandler(
    private val onOpenMedia: (List<String>, Int) -> Unit
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
        println("MediaHandler: updateMedia: question=$questionId, mediaCount=${mediaFiles.size}, files=$mediaFiles")
    }

    fun handleMediaLink(url: String): Boolean {
        println("MediaHandler: handleMediaLink called with URL: $url")
        
        if (url.startsWith("media://")) {
            val fileName = url.substringAfter("media://")
            return openMediaFromCache(fileName)
        }

        if (url.startsWith("file://") && url.contains("/media/")) {
            val fileName = url.substringAfterLast('/')
            return openMediaFromCache(fileName)
        }

        if (!url.contains("/") && !url.startsWith("http") && !url.startsWith("file://")) {
            return openMediaFromCache(url)
        }

        return false
    }

    fun showCurrentMediaGallery(startIndex: Int = 0): Boolean = openMediaFromCache(null, startIndex)

    private fun openMediaFromCache(fileName: String?, fallbackIndex: Int = 0): Boolean {
        if (currentMediaFiles.isEmpty()) {
            println("MediaHandler: No media files available for current question")
            return false
        }
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
