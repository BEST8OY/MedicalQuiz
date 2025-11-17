package com.medicalquiz.app.utils

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles WebView setup, styling, and content rendering for medical quiz content.
 * Provides efficient CSS loading, Material Design theme integration, and optimized WebView configuration.
 */
object WebViewRenderer {

    // ============================================================================
    // Constants
    // ============================================================================

    private const val TAG = "WebViewRenderer"
    private const val BASE_URL = "file:///android_asset/"
    private const val CSS_PLACEHOLDER = "%CSS_CONTENT%"
    private const val CONTENT_PLACEHOLDER = "%CONTENT%"

    private val CSS_ASSET_PATHS = listOf(
        "styles/content.css",
        "styles/tables.css",
        "styles/lists.css",
        "styles/images.css",
        "styles/utilities.css"
    )

    // ============================================================================
    // HTML Template
    // ============================================================================

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
                
                body > * {
                    margin: 0;
                }
                
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
                            var filename = this.getAttribute('data-filename') || this.getAttribute('src');
                            if (filename) {
                                try {
                                    if (window.AndroidBridge && window.AndroidBridge.openMedia) {
                                        window.AndroidBridge.openMedia(String(filename));
                                        return;
                                    }
                                } catch (e) {
                                    console.error('AndroidBridge.openMedia failed', e);
                                }
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

    // ============================================================================
    // Coroutine Scope
    // ============================================================================

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ============================================================================
    // Cache
    // ============================================================================

    @Volatile
    private var cachedCss: String? = null

    @Volatile
    private var cachedThemeCss: String? = null

    // ============================================================================
    // Material Design Theme Variables
    // ============================================================================

    private data class MaterialCssVar(
        val cssName: String,
        val attrId: Int,
        val fallback: Int
    )

    private val MATERIAL_VARS = listOf(
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
        MaterialCssVar("--md-sys-color-background", android.R.attr.colorBackground, Color.WHITE),
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

    // ============================================================================
    // Public API
    // ============================================================================

    /**
     * Configure WebView with optimal settings for medical content display.
     * Should be called once during WebView initialization.
     */
    fun setupWebView(webView: WebView) {
        configureWebViewSettings(webView)
        configureWebViewRendering(webView)
        configureWebViewClients(webView)
    }

    /**
     * Load HTML content with styling into the WebView.
     * Content processing happens on a background thread for performance.
     */
    fun loadContent(context: Context, webView: WebView, htmlContent: String?) {
        if (htmlContent.isNullOrBlank()) {
            webView.safeLoadDataWithBaseURL(data = "")
            return
        }

        backgroundScope.launch(Dispatchers.Default) {
            val processedHtml = processHtmlContent(context, htmlContent)
            
            withContext(Dispatchers.Main) {
                webView.safeLoadDataWithBaseURL(
                    baseUrl = BASE_URL,
                    data = processedHtml
                )
            }
        }
    }

    /**
     * Preload CSS and theme assets in the background to improve first load performance.
     * Should be called early in app lifecycle (e.g., Application.onCreate).
     */
    fun preloadAssets(context: Context) {
        backgroundScope.launch(Dispatchers.IO) {
            loadCssFromAssets(context)
            buildThemeCss(context)
        }
    }

    // ============================================================================
    // WebView Configuration
    // ============================================================================

    private fun configureWebViewSettings(webView: WebView) {
        webView.settings.apply {
            // JavaScript
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false

            // Storage
            domStorageEnabled = false
            // databaseEnabled is deprecated; avoid setting it to remove lint/compat warnings

            // Viewport and Zoom
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false

            // File Access
            allowFileAccess = true

            // Caching
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

            // Security
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
    }

    private fun configureWebViewRendering(webView: WebView) {
        try {
            val layerType = determineOptimalLayerType()
            webView.setLayerType(layerType, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set layer type for WebView", e)
        }

        webView.apply {
            isVerticalScrollBarEnabled = true
            isNestedScrollingEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun determineOptimalLayerType(): Int {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            // Use software rendering on older devices to avoid hardware issues
            View.LAYER_TYPE_SOFTWARE
        } else {
            // Use hardware acceleration for better JS rendering stability
            View.LAYER_TYPE_HARDWARE
        }
    }

    private fun configureWebViewClients(webView: WebView) {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(TAG, "${it.messageLevel()}: ${it.message()}")
                }
                return true
            }
        }
    }

    // ============================================================================
    // HTML Processing
    // ============================================================================

    private fun processHtmlContent(context: Context, htmlContent: String): String {
        val cssContent = buildCssContent(context)
        val sanitizedHtml = HtmlUtils.sanitizeForWebView(htmlContent)

        return HTML_TEMPLATE
            .replace(CSS_PLACEHOLDER, cssContent)
            .replace(CONTENT_PLACEHOLDER, sanitizedHtml)
    }

    // ============================================================================
    // CSS Management
    // ============================================================================

    private fun buildCssContent(context: Context): String {
        val assetCss = loadCssFromAssets(context)
        val themeCss = getOrBuildThemeCss(context)
        return "$assetCss\n$themeCss"
    }

    private fun loadCssFromAssets(context: Context): String {
        cachedCss?.let { return it }

        val combinedCss = CSS_ASSET_PATHS.joinToString("\n") { fileName ->
            context.readAssetOrComment(fileName)
        }

        cachedCss = combinedCss
        return combinedCss
    }

    private fun getOrBuildThemeCss(context: Context): String {
        return cachedThemeCss ?: buildThemeCss(context).also { cachedThemeCss = it }
    }

    private fun buildThemeCss(context: Context): String {
        return buildString {
            append(":root{")
            MATERIAL_VARS.forEach { cssVar ->
                val colorInt = MaterialColors.getColor(context, cssVar.attrId, cssVar.fallback)
                append(cssVar.cssName)
                append(':')
                append(colorInt.toCssHex())
                append(';')
            }
            append("}")
        }
    }

    // ============================================================================
    // Helper Functions
    // ============================================================================

    private fun Context.readAssetOrComment(fileName: String): String {
        return runCatching {
            assets.open(fileName).bufferedReader().use { it.readText() }
        }.getOrElse {
            "/* Error loading $fileName: ${it.message} */"
        }
    }

    private fun Int.toCssHex(): String {
        return String.format("#%06X", 0xFFFFFF and this)
    }
}