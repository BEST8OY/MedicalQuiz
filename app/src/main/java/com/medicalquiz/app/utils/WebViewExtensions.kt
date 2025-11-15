import android.os.Looper
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebView
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extension functions for WebView to ensure thread-safe operations
 */

/**
 * Safely evaluate JavaScript on the WebView, ensuring execution on main thread
 */
fun WebView.safeEvaluateJavascript(script: String, resultCallback: ValueCallback<String>? = null) {
    val runnable = Runnable {
        try {
            this.evaluateJavascript(script, resultCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to evaluate JavaScript: $script", e)
        }
    }

    if (Looper.myLooper() == Looper.getMainLooper()) {
        runnable.run()
    } else {
        post(runnable)
    }
}

/**
 * Safely load URL on the WebView, ensuring execution on main thread
 */
fun WebView.safeLoadUrl(url: String) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        this.loadUrl(url)
    } else {
        post { this.loadUrl(url) }
    }
}

/**
 * Safely load data with base URL on the WebView, ensuring execution on main thread
 */
fun WebView.safeLoadDataWithBaseURL(
    baseUrl: String? = null,
    data: String,
    mimeType: String = "text/html",
    encoding: String = "utf-8",
    historyUrl: String? = null
) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        this.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    } else {
        post { this.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl) }
    }
}

/**
 * Coroutine-friendly wrapper for WebView operations that need main thread
 */
suspend fun WebView.loadSafely(block: WebView.() -> Unit) {
    withContext(Dispatchers.Main) { block() }
}

/**
 * Fire-and-forget wrapper for WebView operations that need main thread
 * Uses GlobalScope for non-suspend contexts
 */
@OptIn(DelicateCoroutinesApi::class)
fun WebView.launchSafely(block: WebView.() -> Unit) {
    GlobalScope.launch(Dispatchers.Main) { block() }
}

private const val TAG = "WebViewExtensions"