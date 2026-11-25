package com.medqb.app.shared.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medqb.app.shared.di.AppGraph
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.platform.rememberDropUnlessResumed
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.screens.media.HtmlViewerScreen

@Composable
fun HtmlViewerEntry(
    key: MedQBRoutes.HtmlViewer,
    graph: AppGraph,
    navigator: AppNavigator,
    mediaHandler: MediaHandler,
) {
    val viewModel = viewModel {
        graph.createHtmlViewerViewModel(key.fileName)
    }
    val htmlDocument by viewModel.htmlDocument.collectAsStateWithLifecycle()
    val richTextScale by viewModel.fontScalePreference.collectAsStateWithLifecycle()

    HtmlViewerScreen(
        fileName = key.fileName,
        htmlContent = htmlDocument?.sanitizedHtml,
        fileExists = htmlDocument?.fileExists ?: true,
        isLoading = htmlDocument == null,
        richTextScale = richTextScale,
        onBack = dropUnlessResumed {
            navigator.navigateBack()
        },
        onLinkClick = rememberDropUnlessResumed { url ->
            mediaHandler.handleMediaLink(url)
        },
        onMediaClick = rememberDropUnlessResumed { ref ->
            mediaHandler.handleMediaLink(ref)
        },
    )
}
