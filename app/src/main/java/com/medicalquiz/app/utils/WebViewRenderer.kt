package com.medicalquiz.app.utils

import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.R as AndroidR
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.*
import android.webkit.ConsoleMessage

object WebViewRenderer {
    private data class MaterialCssVar(
        val cssName: String,
        val attrId: Int,
        val fallback: Int
    )

    private const val CSS_PLACEHOLDER = "%CSS_CONTENT%"
    private const val CONTENT_PLACEHOLDER = "%CONTENT%"
    private const val BASE_URL = "file:///android_asset/"
    private val cssAssetPaths = listOf(
        "styles/content.css",
        "styles/tables.css",
        "styles/lists.css",
        "styles/images.css",
        "styles/utilities.css"
    )

    @Volatile
    private var cachedCss: String? = null
    @Volatile
    private var cachedThemeCss: String? = null
    
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
        MaterialCssVar("--md-sys-color-background", AndroidR.attr.colorBackground, Color.WHITE),
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
                        img.style.cursor = 'pointer';
                        img.onclick = function(event) {
                            event.preventDefault();
                            var filename = this.getAttribute('data-filename');
                            if (filename) {
                                // Use a file:// URL with a "/media/" path segment so the
                                // app's WebViewClient can detect and handle media links.
                                // Some devices/browsers may not support custom schemes like
                                // "media://" so a file:// URL is used for maximum
                                // compatibility and to match MediaHandler expectations.
                                window.location.href = 'file:///media/' + filename;
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
            // Performance optimizations
            settings.apply {
                javaScriptEnabled = true  // Enable for image click handling
                domStorageEnabled = false
                loadWithOverviewMode = true
                useWideViewPort = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = true  // Allow loading files from file:// URLs

                // Additional performance settings
                setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                databaseEnabled = false
                setGeolocationEnabled(false)
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            // Disable hardware acceleration for WebView to prevent crashes on some devices
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

            isVerticalScrollBarEnabled = true
            isNestedScrollingEnabled = false  // Disable nested scrolling for better performance
            setBackgroundColor(Color.TRANSPARENT)

            // Set WebChromeClient for better performance monitoring
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    // Log WebView console messages at debug log level if enabled
                    if (consoleMessage != null && android.util.Log.isLoggable("WebView", android.util.Log.DEBUG)) {
                        android.util.Log.d("WebView", "${consoleMessage.messageLevel()}: ${consoleMessage.message()}")
                    }
                    return true
                }
            }

            // Don't set a default WebViewClient here - let the activity set its own
        }
    }
    
    /**
     * Load HTML content with CSS styling into WebView
     */
    fun loadContent(context: Context, webView: WebView, htmlContent: String?) {
        if (htmlContent.isNullOrBlank()) {
            webView.safeLoadDataWithBaseURL(data = "")
            return
        }

        // Move HTML processing to background thread
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val cssContent = buildCssContent(context)
            val sanitizedHtml = HtmlUtils.sanitizeForWebView(htmlContent)

            val fullHtml = HTML_TEMPLATE
                .replace(CSS_PLACEHOLDER, cssContent)
                .replace(CONTENT_PLACEHOLDER, sanitizedHtml)

            // Switch back to main thread for WebView operations
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                webView.safeLoadDataWithBaseURL(
                    baseUrl = BASE_URL, // Resolve relative links against app assets
                    data = fullHtml
                )
            }
        }
    }    private fun buildCssContent(context: Context): String {
        val assetCss = loadCssFromAssets(context)
        val themeCss = cachedThemeCss ?: buildThemeCss(context).also { cachedThemeCss = it }
        return "$assetCss\n$themeCss"
    }
    
    /**
     * Load all CSS files from assets
     */
    private fun loadCssFromAssets(context: Context): String {
        cachedCss?.let { return it }
        val combinedCss = cssAssetPaths.joinToString("\n") { fileName ->
            context.readAssetOrComment(fileName)
        }
        cachedCss = combinedCss
        return combinedCss
    }

    private fun Context.readAssetOrComment(fileName: String): String =
        runCatching { assets.open(fileName).bufferedReader().use { it.readText() } }
            .getOrElse { "/* Error loading $fileName: ${it.message} */" }

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

    private fun Int.toCssHex(): String = String.format("#%06X", 0xFFFFFF and this)

    /**
     * Preload CSS assets in background to improve first load performance
     */
    fun preloadAssets(context: Context) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Preload CSS
            loadCssFromAssets(context)
            // Preload theme CSS
            buildThemeCss(context)
        }
    }
}
