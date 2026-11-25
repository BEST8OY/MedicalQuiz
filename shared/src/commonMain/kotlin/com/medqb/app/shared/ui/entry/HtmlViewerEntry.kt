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
import com.medqb.app.shared.ui.screens.media.HtmlViewerScreen

@Composable
fun HtmlViewerEntry(
    key: MedQBRoutes.HtmlViewer,
    graph: AppGraph,
    navigator: AppNavigator,
    onOpenMedia: (files: List<String>, startIndex: Int) -> Unit,
    onOpenHtml: (fileName: String) -> Unit,
) {
    val viewModel = viewModel {
        graph.createHtmlViewerViewModel(key.fileName)
    }
    val htmlDocument by viewModel.htmlDocument.collectAsStateWithLifecycle()
    val richTextScale by viewModel.fontScalePreference.collectAsStateWithLifecycle()
    val mediaHandler = remember(onOpenMedia, onOpenHtml) {
        MediaHandler(onOpenMedia = onOpenMedia, onOpenHtml = onOpenHtml)
    }
    val openLink = rememberDropUnlessResumed<String> { target ->
        mediaHandler.handleMediaLink(target)
    }

    HtmlViewerScreen(
        fileName = key.fileName,
        htmlContent = htmlDocument?.sanitizedHtml,
        fileExists = htmlDocument?.fileExists ?: true,
        isLoading = htmlDocument == null,
        richTextScale = richTextScale,
        onBack = dropUnlessResumed {
            navigator.navigateBack()
        },
        onLinkClick = openLink,
        onMediaClick = openLink,
    )
}
