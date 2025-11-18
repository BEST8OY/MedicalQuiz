package com.medicalquiz.app.utils

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import com.medicalquiz.app.ui.MediaHandler
import com.medicalquiz.app.utils.HtmlUtils

/**
 * Controller that wraps common WebView setup used by the quiz.
 * - Sets up optimal WebView settings
 * - Attaches JS bridge that delegates callbacks to the provided `Bridge`
 * - Intercepts file:///media/ links and delegates to MediaHandler
 */
class WebViewController(private val mediaHandler: MediaHandler) {

    interface Bridge {
        fun onAnswerSelected(answerId: Long)
        fun openMedia(mediaRef: String)
    }

    fun setup(webView: WebView, bridge: Bridge) {
        WebViewRenderer.setupWebView(webView)
        // WebChromeClient already configured by `WebViewRenderer.setupWebView`
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url?.toString() ?: return null
                if (url.startsWith("file://") && url.contains("/media/")) {
                    val fileName = url.substringAfterLast('/')
                    
                    // Validate fileName to prevent path traversal attacks
                    if (!isValidMediaFileName(fileName)) {
                        Log.w(TAG, "Rejected unsafe media filename: $fileName")
                        return null
                    }
                    
                    Log.d(TAG, "Intercepting media request for: $fileName")
                    val mediaPath = HtmlUtils.getMediaPath(fileName)
                    if (mediaPath != null) {
                        return try {
                            val file = java.io.File(mediaPath)
                            if (file.exists() && file.canRead()) {
                                val mimeType = getMimeType(fileName)
                                WebResourceResponse(mimeType, "UTF-8", file.inputStream())
                            } else null
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to load media file: $fileName", e)
                            null
                        }
                    }
                }
                return null
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                Log.d(TAG, "shouldOverrideUrlLoading (deprecated) called with URL: $url")
                val handled = mediaHandler.handleMediaLink(url)
                Log.d(TAG, "handleMediaLink returned: $handled for URL: $url")
                return handled
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url?.toString() ?: return false
                Log.d(TAG, "shouldOverrideUrlLoading called with URL: $url")
                val handled = mediaHandler.handleMediaLink(url)
                Log.d(TAG, "handleMediaLink returned: $handled for URL: $url")
                return handled
            }
        }

        webView.addJavascriptInterface(QuizJsBridge(bridge), "AndroidBridge")
    }

    fun applyAnswerState(webView: WebView, correctAnswerId: Int, selectedAnswerId: Int) {
        val js = "applyAnswerState($correctAnswerId, $selectedAnswerId);markAnswerRevealed();revealExplanation();"
        webView.safeEvaluateJavascript(js, null)
    }

    fun loadContent(context: Context, webView: WebView, html: String?) {
        WebViewRenderer.loadContent(context, webView, html)
    }

    private class QuizJsBridge(private val bridge: Bridge) {
        @JavascriptInterface
        fun onAnswerSelected(answerId: String) {
            val parsed = answerId.toLongOrNull() ?: return
            bridge.onAnswerSelected(parsed)
        }

        @JavascriptInterface
        fun openMedia(mediaRef: String) {
            if (mediaRef.isBlank()) return
            bridge.openMedia(mediaRef)
        }
    }

    companion object {
        private const val TAG = "WebViewController"

        /**
         * Validates that a filename is safe for media serving.
         * Rejects filenames with path traversal attempts.
         */
        private fun isValidMediaFileName(fileName: String): Boolean {
            // Reject empty strings
            if (fileName.isBlank()) return false
            
            // Reject path traversal attempts
            if (fileName.contains("..") || 
                fileName.contains("/") || 
                fileName.contains("\\") ||
                fileName.contains("\u0000")) {  // Null byte injection
                return false
            }
            
            return true
        }

        /**
         * Determines MIME type from filename extension.
         * Falls back to octet-stream for unknown or missing extensions.
         */
        private fun getMimeType(fileName: String): String {
            // Extract extension after the last dot
            val lastDotIndex = fileName.lastIndexOf('.')
            if (lastDotIndex <= 0 || lastDotIndex == fileName.length - 1) {
                // No extension found, or dot is at start/end
                return "application/octet-stream"
            }
            
            val extension = fileName.substring(lastDotIndex + 1).lowercase()
            return android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
        }
    }
}
