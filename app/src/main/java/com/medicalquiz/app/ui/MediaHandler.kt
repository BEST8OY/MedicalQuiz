package com.medicalquiz.app.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import com.medicalquiz.app.MediaViewerActivity

/**
 * Handles media metadata caching and gallery launches per question.
 */
class MediaHandler(private val context: Context) {
    private var currentQuestionId: Long? = null
    private var currentMediaFiles: List<String> = emptyList()

    fun reset() {
        currentQuestionId = null
        currentMediaFiles = emptyList()
    }

    fun updateMedia(questionId: Long, mediaFiles: List<String>) {
        currentQuestionId = questionId
        currentMediaFiles = mediaFiles
        Log.d(TAG, "updateMedia: question=$questionId, mediaCount=${mediaFiles.size}, files=$mediaFiles")
    }

    fun handleMediaLink(url: String): Boolean {
        Log.d(TAG, "handleMediaLink called with URL: $url")
        Log.d(TAG, "Current media files: $currentMediaFiles")
        
        // Support both file://.../media/... links and the legacy media://<filename> scheme
        if (url.startsWith("media://")) {
            val fileName = url.substringAfter("media://")
            Log.d(TAG, "Detected media:// scheme, fileName: $fileName")
            return openMediaFromCache(fileName)
        }

        if (url.startsWith(FILE_SCHEME) && url.contains(MEDIA_PATH_SEGMENT)) {
            val fileName = url.substringAfterLast('/')
            Log.d(TAG, "Detected file:///media/ scheme, fileName: $fileName")
            return openMediaFromCache(fileName)
        }

        // If it's just a filename (from JS bridge or direct call), treat it as a media filename
        if (!url.contains("/") && !url.startsWith("http") && !url.startsWith("file://")) {
            Log.d(TAG, "Detected bare filename: $url")
            return openMediaFromCache(url)
        }

        Log.d(TAG, "URL does not match any known media pattern")
        return false
    }

    fun showCurrentMediaGallery(startIndex: Int = 0): Boolean = openMediaFromCache(null, startIndex)

    private fun openMediaFromCache(fileName: String?, fallbackIndex: Int = 0): Boolean {
        if (currentMediaFiles.isEmpty()) {
            Log.w(TAG, "No media files available for current question")
            return false
        }
        val startIndex = resolveStartIndex(currentMediaFiles, fileName, fallbackIndex)
        Log.d(TAG, "Opening media viewer with startIndex=$startIndex, fileName=$fileName")
        openMediaViewer(currentMediaFiles, startIndex)
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
        Log.d(TAG, "openMediaViewer: mediaCount=${mediaFiles.size}, startIndex=$startIndex")
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
        private const val TAG = "MediaHandler"
        private const val FILE_SCHEME = "file://"
        private const val MEDIA_PATH_SEGMENT = "/media/"
    }
}
