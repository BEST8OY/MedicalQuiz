package com.medicalquiz.app.shared.data

class CacheManager {
    private val mediaFilesCache = mutableMapOf<Long, List<String>>()

    fun updateMediaCache(questionId: Long, mediaFiles: List<String>) {
        if (mediaFiles.isEmpty()) mediaFilesCache.remove(questionId) else mediaFilesCache[questionId] = mediaFiles
    }

    fun getMediaFiles(questionId: Long): List<String> = mediaFilesCache[questionId] ?: emptyList()

    fun clearCaches() {
        mediaFilesCache.clear()
    }

    fun trimCachesIfNeeded(currentIndex: Int, maxSize: Int = 1000) {
        if (currentIndex > 0 && currentIndex % 50 == 0) {
            if (mediaFilesCache.size > maxSize) {
                mediaFilesCache.clear() // Simple clear for now
            }
        }
    }
}
