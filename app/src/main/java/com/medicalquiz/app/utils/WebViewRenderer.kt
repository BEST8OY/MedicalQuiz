package com.medicalquiz.app.utils

import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient

object WebViewRenderer {
    
    private const val HTML_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                %CSS_CONTENT%
                
                /* Base styles */
                body {
                    margin: 0;
                    padding: 16px;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    font-size: 16px;
                    line-height: 1.6;
                    color: #1f2937;
                    background-color: transparent;
                }
                
                /* Apply medical-content class to all content */
                body > * {
                    margin: 0;
                }
                
                /* Make images clickable */
                img {
                    cursor: pointer;
                }
            </style>
            <script>
                function makeImagesClickable() {
                    document.querySelectorAll('img').forEach(function(img) {
                        img.onclick = function() {
                            var src = this.src;
                            // Extract filename from file:// URL
                            if (src.startsWith('file://')) {
                                window.location.href = src;
                            }
                        };
                    });
                }
                window.onload = makeImagesClickable;
            </script>
        </head>
        <body class="medical-content">
            %CONTENT%
        </body>
        </html>
    """
    
    /**
     * Configure WebView with optimal settings for medical content
     */
    fun setupWebView(webView: WebView) {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true  // Enable for image click handling
                domStorageEnabled = false
                loadWithOverviewMode = true
                useWideViewPort = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }
            setBackgroundColor(Color.TRANSPARENT)
            webViewClient = WebViewClient()
        }
    }
    
    /**
     * Load HTML content with CSS styling into WebView
     */
    fun loadContent(context: Context, webView: WebView, htmlContent: String?) {
        if (htmlContent.isNullOrBlank()) {
            webView.loadData("", "text/html", "UTF-8")
            return
        }
        
        val cssContent = loadCssFromAssets(context)
        val sanitizedHtml = HtmlUtils.sanitizeForWebView(htmlContent)
        
        val fullHtml = HTML_TEMPLATE
            .replace("%CSS_CONTENT%", cssContent)
            .replace("%CONTENT%", sanitizedHtml)
        
        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            fullHtml,
            "text/html",
            "UTF-8",
            null
        )
    }
    
    /**
     * Load all CSS files from assets
     */
    private fun loadCssFromAssets(context: Context): String {
        val cssFiles = listOf(
            "styles/content.css",
            "styles/tables.css",
            "styles/lists.css",
            "styles/images.css",
            "styles/utilities.css"
        )
        
        return cssFiles.joinToString("\n") { fileName ->
            try {
                context.assets.open(fileName).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                "/* Error loading $fileName: ${e.message} */"
            }
        }
    }
}
