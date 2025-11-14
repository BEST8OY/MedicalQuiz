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
        if (!url.startsWith("file://") || !url.contains("/media/")) return false
        val fileName = url.substringAfterLast("/")
        return openMediaFromCache(fileName)
    }

    fun showCurrentMediaGallery(startIndex: Int = 0): Boolean = openMediaFromCache(null, startIndex)

    private fun openMediaFromCache(fileName: String?, fallbackIndex: Int = 0): Boolean {
        val questionId = currentQuestionId ?: return false
        val mediaFiles = mediaCache[questionId].orEmpty()
        if (mediaFiles.isEmpty()) return false
        val startIndex = fileName?.let {
            mediaFiles.indexOfFirst { cached -> cached.equals(it, ignoreCase = true) }
        }?.takeIf { it >= 0 } ?: fallbackIndex.coerceIn(0, mediaFiles.lastIndex)
        openMediaViewer(mediaFiles, startIndex)
        return true
    }

    fun openMediaViewer(mediaFiles: List<String>, startIndex: Int) {
        val intent = Intent(context, MediaViewerActivity::class.java)
        intent.putStringArrayListExtra("MEDIA_FILES", ArrayList(mediaFiles))
        intent.putExtra("START_INDEX", startIndex)
        context.startActivity(intent)
    }
}
