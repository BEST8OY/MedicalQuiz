package com.medicalquiz.app.utils

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medicalquiz.app.ui.MediaHandler
import kotlinx.coroutines.flow.Flow

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
    val colorScheme = MaterialTheme.colorScheme
    val cssVariables = remember(colorScheme) { buildCssVariables(colorScheme) }
    val cssSignature = remember(cssVariables) {
        cssVariables.entries.joinToString(separator = "|") { "${it.key}=${it.value}" }
    }
    var appliedCssSignature by remember { mutableStateOf<String?>(null) }
    
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
                    appliedCssSignature = null
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

            if (cssSignature != appliedCssSignature) {
                val script = buildCssVariableScript(cssVariables)
                webView.safeEvaluateJavascript(script, null)
                appliedCssSignature = cssSignature
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun buildCssVariables(colorScheme: ColorScheme): Map<String, String> {
    return mapOf(
        "--md-sys-color-primary" to colorScheme.primary.toCssHex(),
        "--md-sys-color-on-primary" to colorScheme.onPrimary.toCssHex(),
        "--md-sys-color-primary-container" to colorScheme.primaryContainer.toCssHex(),
        "--md-sys-color-on-primary-container" to colorScheme.onPrimaryContainer.toCssHex(),
        "--md-sys-color-secondary" to colorScheme.secondary.toCssHex(),
        "--md-sys-color-on-secondary" to colorScheme.onSecondary.toCssHex(),
        "--md-sys-color-secondary-container" to colorScheme.secondaryContainer.toCssHex(),
        "--md-sys-color-on-secondary-container" to colorScheme.onSecondaryContainer.toCssHex(),
        "--md-sys-color-tertiary" to colorScheme.tertiary.toCssHex(),
        "--md-sys-color-on-tertiary" to colorScheme.onTertiary.toCssHex(),
        "--md-sys-color-tertiary-container" to colorScheme.tertiaryContainer.toCssHex(),
        "--md-sys-color-on-tertiary-container" to colorScheme.onTertiaryContainer.toCssHex(),
        "--md-sys-color-surface" to colorScheme.surface.toCssHex(),
        "--md-sys-color-on-surface" to colorScheme.onSurface.toCssHex(),
        "--md-sys-color-surface-variant" to colorScheme.surfaceVariant.toCssHex(),
        "--md-sys-color-on-surface-variant" to colorScheme.onSurfaceVariant.toCssHex(),
        "--md-sys-color-background" to colorScheme.background.toCssHex(),
        "--md-sys-color-on-background" to colorScheme.onBackground.toCssHex(),
        "--md-sys-color-outline" to colorScheme.outline.toCssHex(),
        "--md-sys-color-outline-variant" to colorScheme.outlineVariant.toCssHex(),
        "--md-sys-color-error" to colorScheme.error.toCssHex(),
        "--md-sys-color-on-error" to colorScheme.onError.toCssHex(),
        "--md-sys-color-error-container" to colorScheme.errorContainer.toCssHex(),
        "--md-sys-color-on-error-container" to colorScheme.onErrorContainer.toCssHex(),
        "--md-sys-color-success" to colorScheme.tertiary.toCssHex(),
        "--md-sys-color-on-success" to colorScheme.onTertiary.toCssHex(),
        "--md-sys-color-warning" to colorScheme.secondary.toCssHex(),
        "--md-sys-color-warning-container" to colorScheme.secondaryContainer.toCssHex(),
        "--md-sys-color-on-warning-container" to colorScheme.onSecondaryContainer.toCssHex()
    )
}

private fun buildCssVariableScript(variables: Map<String, String>): String {
    val assignments = variables.entries.joinToString(separator = "") { (key, value) ->
        "root.style.setProperty('$key','$value');"
    }
    return "(function(){var root=document.documentElement;if(!root)return;$assignments})();"
}

private fun Color.toCssHex(): String {
    val argb = toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return String.format("#%02X%02X%02X", r, g, b)
}