package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.data.MediaDescriptionRepository
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.ui.media.MediaType
import com.medqb.app.shared.utils.MediaTypeUtils
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encapsulates the logic that was previously inline inside
 * `navigateToMediaViewer` in App.kt: filtering playable files,
 * loading descriptions, and producing the navigation route.
 */
@Inject
class MediaNavigationCoordinator(
    private val localContentRepository: LocalContentRepository,
    private val mediaDescriptionRepository: MediaDescriptionRepository,
) {

    /**
     * Resolves a list of file names into a [MedQBRoutes.MediaViewer] route that is
     * ready to be applied to the navigator, or `null` when no playable
     * media files are available.
     *
     * This resolution does not block navigation on loading descriptions.
     */
    suspend fun resolveMediaViewerRoute(
        files: List<String>,
        startIndex: Int,
    ): MedQBRoutes.MediaViewer? {
        val availableFiles = mutableListOf<String>()
        for (fileName in files) {
            val isPlayableType = when (MediaTypeUtils.fromFileName(fileName)) {
                MediaType.IMAGE,
                MediaType.VIDEO,
                MediaType.AUDIO -> true
                else -> false
            }
            if (!isPlayableType) continue

            if (localContentRepository.mediaFileExists(fileName)) {
                availableFiles.add(fileName)
            }
        }

        if (availableFiles.isEmpty()) return null

        val originalFile = files.getOrNull(startIndex)
        val newIndex = if (originalFile != null) {
            availableFiles.indexOf(originalFile).coerceAtLeast(0)
        } else 0
        val safeIndex = newIndex.coerceIn(0, availableFiles.lastIndex)

        return MedQBRoutes.MediaViewer(
            files = availableFiles,
            startIndex = safeIndex,
        )
    }

    /**
     * Loads media descriptions asynchronously with IO dispatcher.
     */
    suspend fun loadDescriptions(): Map<String, MediaDescription> {
        return withContext(Dispatchers.IO) {
            mediaDescriptionRepository.load()
        }
    }
}
