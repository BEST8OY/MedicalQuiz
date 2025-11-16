package com.medicalquiz.app.utils

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.medicalquiz.app.ui.MediaHandler
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.LaunchedEffect

data class WebViewState(
    val html: String? = null,
    val correctAnswerId: Int? = null,
    val selectedAnswerId: Int? = null
)

@Composable
fun WebViewComposable(
    stateFlow: Flow<WebViewState>,
    onAnswerSelected: (Long) -> Unit,
    onOpenMedia: (String) -> Unit,
    mediaHandler: MediaHandler
) {
    val context = LocalContext.current
    val webViewController = remember { WebViewController(mediaHandler) }
    var currentState by remember { mutableStateOf(WebViewState()) }

    LaunchedEffect(stateFlow) {
        stateFlow.collect { state ->
            currentState = state
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewController.setup(this, object : WebViewController.Bridge {
                    override fun onAnswerSelected(answerId: Long) {
                        onAnswerSelected(answerId)
                    }

                    override fun openMedia(mediaRef: String) {
                        onOpenMedia(mediaRef)
                    }
                })
            }
        },
        update = { webView ->
            currentState.html?.let { html ->
                webViewController.loadContent(context, webView, html)
            }
            currentState.correctAnswerId?.let { correct ->
                currentState.selectedAnswerId?.let { selected ->
                    webViewController.applyAnswerState(webView, correct, selected)
                }
            }
        }
    )
}