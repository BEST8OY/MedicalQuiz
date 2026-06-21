package com.medqb.app.shared.data

import dev.zacsweers.metro.Inject

@Inject
class CacheManager {
    /**
     * Periodic cleanup trigger. Called after loading questions to prevent memory buildup.
     */
    fun trimCachesIfNeeded(currentIndex: Int) {
        // Placeholder for future cache management if needed
    }
}
