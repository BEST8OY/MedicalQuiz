package com.medicalquiz.app.ui

import android.content.Context
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.LifecycleCoroutineScope
import com.medicalquiz.app.MediaViewerActivity
import com.medicalquiz.app.data.database.DatabaseManager
import com.medicalquiz.app.utils.HtmlUtils
import kotlinx.coroutines.launch

/**
 * Handler for media-related operations in quiz
 */
class MediaHandler(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private var databaseManager: DatabaseManager,
    private val getCurrentQuestionId: () -> Long?
) {

    fun updateDatabaseManager(newManager: DatabaseManager) {
        databaseManager = newManager
    }
    
    fun setupWebViewImageClicks(webView: WebView) {
        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("file://") && url.contains("/media/")) {
                    val fileName = url.substringAfterLast("/")
                    openMediaViewerForFile(fileName)
                    return true
                }
                return false
            }
        }
        
        webView.settings.javaScriptEnabled = true
        webView.webChromeClient = WebChromeClient()
    }
    
    private fun openMediaViewerForFile(fileName: String) {
        lifecycleScope.launch {
            val questionId = getCurrentQuestionId() ?: return@launch
            val currentQuestion = databaseManager.getQuestionById(questionId)
            
            if (currentQuestion != null) {
                val mediaFiles = mutableListOf<String>()
                currentQuestion.mediaName?.let { mediaFiles.add(it) }
                HtmlUtils.parseMediaFiles(currentQuestion.otherMedias).let { mediaFiles.addAll(it) }
                
                val startIndex = mediaFiles.indexOf(fileName).takeIf { it >= 0 } ?: 0
                openMediaViewer(mediaFiles, startIndex)
            }
        }
    }
    
    fun openMediaViewer(mediaFiles: List<String>, startIndex: Int) {
        val intent = Intent(context, MediaViewerActivity::class.java)
        intent.putStringArrayListExtra("MEDIA_FILES", ArrayList(mediaFiles))
        intent.putExtra("START_INDEX", startIndex)
        context.startActivity(intent)
    }
}
