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
        fun domReady(questionId: String)
    }

    fun setup(webView: WebView, bridge: Bridge) {
        WebViewRenderer.setupWebView(webView)
        // WebChromeClient already configured by `WebViewRenderer.setupWebView`
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url?.toString() ?: return null
                if (url.startsWith("file://") && url.contains("/media/")) {
                    val fileName = url.substringAfterLast('/')
                    Log.d(TAG, "Intercepting media request for: $fileName")
                    val mediaPath = HtmlUtils.getMediaPath(fileName)
                    if (mediaPath != null) {
                        return try {
                            val file = java.io.File(mediaPath)
                            if (file.exists() && file.canRead()) {
                                val mimeType = WebViewController.getMimeType(fileName)
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
                return mediaHandler.handleMediaLink(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url?.toString() ?: return false
                return mediaHandler.handleMediaLink(url)
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

            @JavascriptInterface
            fun domReady(questionId: String) {
                if (questionId.isBlank()) return
                bridge.domReady(questionId)
            }
    }

    companion object {
        private const val TAG = "WebViewController"

        private fun getMimeType(fileName: String): String {
            return android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(fileName.substringAfterLast('.', ""))
                ?: "application/octet-stream"
        }
    }
}
