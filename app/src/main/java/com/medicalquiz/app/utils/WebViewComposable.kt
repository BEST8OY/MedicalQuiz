package com.medicalquiz.app.utils

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.ui.MediaHandler
import kotlinx.coroutines.flow.Flow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

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
    
    // Collect state from flow with proper lifecycle management
    val currentState by stateFlow.collectAsStateWithLifecycle(
        initialValue = WebViewState()
    )
    
    // Track what's been applied to avoid redundant WebView operations
    var appliedHtml by remember { mutableStateOf<String?>(null) }
    var appliedAnswerState by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                // Enable proper scrolling and zoom
                settings.apply {
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                
                // Configure scrolling behavior
                isVerticalScrollBarEnabled = true
                scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY
                
                // Enable nested scrolling for better Compose integration
                isNestedScrollingEnabled = true
                
                webViewController.setup(this, object : WebViewController.Bridge {
                    override fun onAnswerSelected(answerId: Long) {
                        onAnswerSelected(answerId)
                    }

                    override fun openMedia(mediaRef: String) {
                        android.util.Log.d("WebViewComposable", "openMedia Bridge called with: $mediaRef")
                        onOpenMedia(mediaRef)
                    }
                })
            }
        },
        update = { webView ->
            // Only load HTML if it has changed
            currentState.html?.let { html ->
                if (html != appliedHtml) {
                    webViewController.loadContent(context, webView, html)
                    appliedHtml = html
                    // Reset answer state when new question loads
                    appliedAnswerState = null
                }
            }
            
            // Only apply answer state if it has changed
            val newAnswerState = currentState.correctAnswerId?.let { correct ->
                currentState.selectedAnswerId?.let { selected ->
                    Pair(correct, selected)
                }
            }
            
            if (newAnswerState != null && newAnswerState != appliedAnswerState) {
                webViewController.applyAnswerState(webView, newAnswerState.first, newAnswerState.second)
                appliedAnswerState = newAnswerState
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = Modifier.fillMaxSize()
    )
}