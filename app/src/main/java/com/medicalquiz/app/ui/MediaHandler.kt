package com.medicalquiz.app.ui

import android.content.Context
import android.content.Intent
import com.medicalquiz.app.MediaViewerActivity

/**
 * Handles media metadata caching and gallery launches per question.
 */
class MediaHandler(private val context: Context) {
    private val mediaCache = mutableMapOf<Long, List<String>>()
    private var currentQuestionId: Long? = null

    fun reset() {
        mediaCache.clear()
        currentQuestionId = null
    }

    fun updateMedia(questionId: Long, mediaFiles: List<String>) {
        mediaCache[questionId] = mediaFiles
        currentQuestionId = questionId
    }

    fun handleMediaLink(url: String): Boolean {
        if (!url.startsWith(FILE_SCHEME) || !url.contains(MEDIA_PATH_SEGMENT)) return false
        val fileName = url.substringAfterLast('/')
        return openMediaFromCache(fileName)
    }

    fun showCurrentMediaGallery(startIndex: Int = 0): Boolean = openMediaFromCache(null, startIndex)

    private fun openMediaFromCache(fileName: String?, fallbackIndex: Int = 0): Boolean {
        val mediaFiles = currentQuestionId?.let { mediaCache[it] }.orEmpty()
        if (mediaFiles.isEmpty()) return false
        val startIndex = resolveStartIndex(mediaFiles, fileName, fallbackIndex)
        openMediaViewer(mediaFiles, startIndex)
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

    fun openMediaViewer(mediaFiles: List<String>, startIndex: Int) {
        val intent = Intent(context, MediaViewerActivity::class.java).apply {
            putStringArrayListExtra(
                MediaViewerActivity.EXTRA_MEDIA_FILES,
                ArrayList(mediaFiles)
            )
            putExtra(MediaViewerActivity.EXTRA_START_INDEX, startIndex)
        }
        context.startActivity(intent)
    }

    private companion object {
        private const val FILE_SCHEME = "file://"
        private const val MEDIA_PATH_SEGMENT = "/media/"
    }
}
