package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.dropUnlessResumed
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.screens.media.HtmlViewerScreen

@Composable
fun HtmlViewerEntry(
    key: MedQBRoutes.HtmlViewer,
    graph: AppGraph,
    navigator: AppNavigator,
    mediaHandler: MediaHandler,
) {
    val htmlDocument by produceState<LocalContentRepository.HtmlDocumentResult?>(
        initialValue = null,
        key1 = key.fileName,
    ) {
        value = graph.localContentRepository.loadHtmlDocument(key.fileName)
    }

    HtmlViewerScreen(
        fileName = key.fileName,
        htmlContent = htmlDocument?.sanitizedHtml,
        fileExists = htmlDocument?.fileExists ?: true,
        isLoading = htmlDocument == null,
        onBack = dropUnlessResumed {
            navigator.navigateBack()
        },
        onLinkClick = { url ->
            mediaHandler.handleMediaLink(url)
        }
    )
}
