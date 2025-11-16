package com.medicalquiz.app.data

import com.medicalquiz.app.utils.HtmlUtils

/**
 * Small caching layer used by the ViewModel and Activity to hold media lists and perform trimming.
 */
class CacheManager {
    private val mediaFilesCache = mutableMapOf<Long, List<String>>()

    fun updateMediaCache(questionId: Long, mediaFiles: List<String>) {
        if (mediaFiles.isEmpty()) mediaFilesCache.remove(questionId) else mediaFilesCache[questionId] = mediaFiles
    }

    fun getMediaFiles(questionId: Long): List<String> = mediaFilesCache[questionId] ?: emptyList()

    fun clearCaches() {
        mediaFilesCache.clear()
        HtmlUtils.clearMediaCaches()
    }

    fun trimCachesIfNeeded(currentIndex: Int, maxSize: Int = 1000) {
        if (currentIndex > 0 && currentIndex % 50 == 0) {
            HtmlUtils.trimCaches(maxSize)
        }
    }
}
