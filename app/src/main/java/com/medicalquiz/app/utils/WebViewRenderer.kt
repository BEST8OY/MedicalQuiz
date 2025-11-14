package com.medicalquiz.app.utils

import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors

object WebViewRenderer {
    private data class MaterialCssVar(
        val cssName: String,
        val attrId: Int,
        val fallback: Int
    )
    
    private val materialVars = listOf(
        MaterialCssVar("--md-sys-color-primary", MaterialR.attr.colorPrimary, Color.parseColor("#2563eb")),
        MaterialCssVar("--md-sys-color-on-primary", MaterialR.attr.colorOnPrimary, Color.WHITE),
        MaterialCssVar("--md-sys-color-primary-container", MaterialR.attr.colorPrimaryContainer, Color.parseColor("#dbeafe")),
        MaterialCssVar("--md-sys-color-on-primary-container", MaterialR.attr.colorOnPrimaryContainer, Color.parseColor("#082f49")),
        MaterialCssVar("--md-sys-color-secondary", MaterialR.attr.colorSecondary, Color.parseColor("#475569")),
        MaterialCssVar("--md-sys-color-on-secondary", MaterialR.attr.colorOnSecondary, Color.WHITE),
        MaterialCssVar("--md-sys-color-secondary-container", MaterialR.attr.colorSecondaryContainer, Color.parseColor("#e2e8f0")),
        MaterialCssVar("--md-sys-color-on-secondary-container", MaterialR.attr.colorOnSecondaryContainer, Color.parseColor("#0f172a")),
        MaterialCssVar("--md-sys-color-tertiary", MaterialR.attr.colorTertiary, Color.parseColor("#9333ea")),
        MaterialCssVar("--md-sys-color-on-tertiary", MaterialR.attr.colorOnTertiary, Color.WHITE),
        MaterialCssVar("--md-sys-color-tertiary-container", MaterialR.attr.colorTertiaryContainer, Color.parseColor("#f3e8ff")),
        MaterialCssVar("--md-sys-color-on-tertiary-container", MaterialR.attr.colorOnTertiaryContainer, Color.parseColor("#581c87")),
        MaterialCssVar("--md-sys-color-surface", MaterialR.attr.colorSurface, Color.WHITE),
        MaterialCssVar("--md-sys-color-on-surface", MaterialR.attr.colorOnSurface, Color.parseColor("#1f2937")),
        MaterialCssVar("--md-sys-color-surface-variant", MaterialR.attr.colorSurfaceVariant, Color.parseColor("#f3f4f6")),
        MaterialCssVar("--md-sys-color-on-surface-variant", MaterialR.attr.colorOnSurfaceVariant, Color.parseColor("#374151")),
        MaterialCssVar("--md-sys-color-background", MaterialR.attr.colorBackground, Color.WHITE),
        MaterialCssVar("--md-sys-color-on-background", MaterialR.attr.colorOnBackground, Color.parseColor("#1f2937")),
        MaterialCssVar("--md-sys-color-outline", MaterialR.attr.colorOutline, Color.parseColor("#d1d5db")),
        MaterialCssVar("--md-sys-color-outline-variant", MaterialR.attr.colorOutlineVariant, Color.parseColor("#9ca3af")),
        MaterialCssVar("--md-sys-color-error", MaterialR.attr.colorError, Color.parseColor("#dc2626")),
        MaterialCssVar("--md-sys-color-on-error", MaterialR.attr.colorOnError, Color.WHITE),
        MaterialCssVar("--md-sys-color-error-container", MaterialR.attr.colorErrorContainer, Color.parseColor("#fee2e2")),
        MaterialCssVar("--md-sys-color-on-error-container", MaterialR.attr.colorOnErrorContainer, Color.parseColor("#7f1d1d")),
        MaterialCssVar("--md-sys-color-success", MaterialR.attr.colorTertiary, Color.parseColor("#22c55e")),
        MaterialCssVar("--md-sys-color-on-success", MaterialR.attr.colorOnTertiary, Color.parseColor("#065f46")),
        MaterialCssVar("--md-sys-color-warning", MaterialR.attr.colorTertiary, Color.parseColor("#f59e0b")),
        MaterialCssVar("--md-sys-color-warning-container", MaterialR.attr.colorTertiaryContainer, Color.parseColor("#fffbeb")),
        MaterialCssVar("--md-sys-color-on-warning-container", MaterialR.attr.colorOnTertiaryContainer, Color.parseColor("#78350f"))
    )
    
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
        
        val cssContent = buildCssContent(context)
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

    private fun buildCssContent(context: Context): String {
        return buildString {
            append(loadCssFromAssets(context))
            append('\n')
            append(buildThemeCss(context))
        }
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

    private fun buildThemeCss(context: Context): String {
        return buildString {
            append(":root{")
            materialVars.forEach { cssVar ->
                val colorInt = MaterialColors.getColor(context, cssVar.attrId, cssVar.fallback)
                append(cssVar.cssName)
                append(':')
                append(colorInt.toCssHex())
                append(';')
            }
            append("}")
        }
    }

    private fun Int.toCssHex(): String {
        val rgb = this and 0x00FFFFFF
        return String.format("#%06X", rgb)
    }
}
