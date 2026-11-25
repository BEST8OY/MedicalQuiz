package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.data.MediaDescriptionRepository
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.domain.SnackbarSink
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.ui.media.MediaType
import com.medqb.app.shared.utils.MediaTypeUtils
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Encapsulates media routing, description loading, and background media saving.
 */
@Inject
@SingleIn(AppScope::class)
class MediaNavigationCoordinator(
    private val localContentRepository: LocalContentRepository,
    private val mediaDescriptionRepository: MediaDescriptionRepository,
    private val snackbarSink: SnackbarSink,
    private val applicationScope: CoroutineScope,
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
    ): MedQBRoutes.MediaViewer? = withContext(Dispatchers.IO) {
        val availableFiles = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (fileName in files) {
            val isPlayableType = when (MediaTypeUtils.fromFileName(fileName)) {
                MediaType.IMAGE,
                MediaType.VIDEO,
                MediaType.AUDIO -> true
                else -> false
            }
            if (!isPlayableType) continue

            if (seen.add(fileName) && localContentRepository.mediaFileExists(fileName)) {
                availableFiles.add(fileName)
            }
        }

        if (availableFiles.isEmpty()) return@withContext null

        val originalFile = files.getOrNull(startIndex)
        val safeIndex = if (originalFile != null) {
            availableFiles.indexOf(originalFile).coerceAtLeast(0)
        } else 0

        MedQBRoutes.MediaViewer(
            files = availableFiles,
            startIndex = safeIndex,
        )
    }

    fun resolveMediaFilePath(fileName: String): String =
        localContentRepository.mediaFilePath(fileName)

    suspend fun mediaFileExists(fileName: String): Boolean =
        localContentRepository.mediaFileExists(fileName)

    suspend fun resolveOverlayPaths(files: List<String>): Map<String, String?> =
        localContentRepository.resolveOverlayPaths(files)

    /**
     * Loads media descriptions asynchronously with IO dispatcher.
     */
    suspend fun loadDescriptions(): Map<String, MediaDescription> {
        return withContext(Dispatchers.IO) {
            mediaDescriptionRepository.load()
        }
    }

    /**
     * Executes media saving in an application-scoped background coroutine, ensuring
     * completion even if the user navigates away or pops the media viewer screen.
     */
    fun saveMediaFileAsync(fileName: String) {
        applicationScope.launch {
            when (val result = localContentRepository.saveMediaFile(fileName)) {
                is LocalContentRepository.SaveMediaResult.Success ->
                    snackbarSink.emitSnackbar(SnackbarMessage.Simple("Media saved to: ${result.destPath}"))
                LocalContentRepository.SaveMediaResult.InvalidFileName ->
                    snackbarSink.emitSnackbar(SnackbarMessage.Simple("Invalid file name"))
                LocalContentRepository.SaveMediaResult.CopyFailed ->
                    snackbarSink.emitSnackbar(SnackbarMessage.Simple("Failed to save media"))
            }
        }
    }
}
