package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.screens.media.MediaViewerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun MediaViewerEntry(
    key: MedQBRoutes.MediaViewer,
    graph: AppGraph,
    navigator: AppNavigator,
    mediaHandler: MediaHandler,
    mediaDescriptionsFlow: MutableStateFlow<Map<String, MediaDescription>>,
) {
    val scope = rememberCoroutineScope()
    val mediaDescriptions by mediaDescriptionsFlow.collectAsStateWithLifecycle()
    val fontScalePreference = graph.settingsRepository.fontScalePreference
        .collectAsStateWithLifecycle(null).value

    MediaViewerScreen(
        mediaFiles = key.files,
        startIndex = key.startIndex,
        mediaDescriptions = mediaDescriptions,
        richTextScale = fontScalePreference ?: 1f,
        resolveMediaFilePath = graph.localContentRepository::mediaFilePath,
        mediaFileExists = { fileName ->
            graph.localContentRepository.mediaFileExists(fileName)
        },
        resolveOverlayPaths = { files ->
            graph.localContentRepository.resolveOverlayPaths(files)
        },
        onLinkClick = { url ->
            mediaHandler.handleMediaLink(url)
        },
        onSaveMedia = { fileName ->
            scope.launch {
                when (val result = graph.localContentRepository.saveMediaFile(fileName)) {
                    is LocalContentRepository.SaveMediaResult.Success ->
                        graph.snackbarDispatcher.emitSnackbar("Media saved to: ${result.destPath}")
                    LocalContentRepository.SaveMediaResult.InvalidFileName ->
                        graph.snackbarDispatcher.emitSnackbar("Invalid file name")
                    LocalContentRepository.SaveMediaResult.CopyFailed ->
                        graph.snackbarDispatcher.emitSnackbar("Failed to save media")
                }
            }
        },
        onBack = {
            navigator.navigateBack()
        }
    )
}
