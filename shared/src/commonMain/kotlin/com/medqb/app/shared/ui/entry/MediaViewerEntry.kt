package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.platform.rememberDropUnlessResumed
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.screens.media.MediaViewerScreen

@Composable
fun MediaViewerEntry(
    key: MedQBRoutes.MediaViewer,
    graph: AppGraph,
    navigator: AppNavigator,
    onOpenMedia: (files: List<String>, startIndex: Int) -> Unit,
    onOpenHtml: (fileName: String) -> Unit,
) {
    val viewModel = viewModel {
        graph.createMediaViewerViewModel()
    }
    val mediaDescriptions by viewModel.mediaDescriptions.collectAsStateWithLifecycle()
    val fontScalePreference by viewModel.fontScalePreference.collectAsStateWithLifecycle()
    val mediaHandler = remember(onOpenMedia, onOpenHtml) {
        MediaHandler(onOpenMedia = onOpenMedia, onOpenHtml = onOpenHtml)
    }

    MediaViewerScreen(
        mediaFiles = key.files,
        startIndex = key.startIndex,
        mediaDescriptions = mediaDescriptions,
        richTextScale = fontScalePreference,
        resolveMediaFilePath = viewModel::resolveMediaFilePath,
        mediaFileExists = viewModel::mediaFileExists,
        resolveOverlayPaths = viewModel::resolveOverlayPaths,
        onLinkClick = rememberDropUnlessResumed { url ->
            mediaHandler.handleMediaLink(url)
        },
        onSaveMedia = viewModel::saveMedia,
        onBack = dropUnlessResumed {
            navigator.navigateBack()
        }
    )
}
