package com.medicalquiz.app

import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.medicalquiz.app.ui.MediaViewerScreen
import com.medicalquiz.app.ui.theme.MedicalQuizTheme
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.ComposeView
import androidx.activity.OnBackPressedCallback
import java.io.File

class MediaViewerActivity : AppCompatActivity() {
    private var mediaFiles = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge to allow immersive media viewing
        WindowCompat.enableEdgeToEdge(window)
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !isDark
        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(this, "Storage permission required to view media", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        supportActionBar?.title = "Media Viewer"

        val allMediaFiles = intent.getStringArrayListExtra(EXTRA_MEDIA_FILES) ?: arrayListOf()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

        mediaFiles = allMediaFiles.filter(::isMediaAvailable)
        if (mediaFiles.isEmpty()) {
            finish()
            return
        }

        val actualStartIndex = mediaFiles.indexOf(allMediaFiles.getOrNull(startIndex)).takeIf { it != -1 } ?: 0

        // Add back press handler to close media viewer and return to quiz
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        setContentView(ComposeView(this).apply {
            setContent {
                MedicalQuizTheme {
                    MediaViewerScreen(mediaFiles = mediaFiles, startIndex = actualStartIndex)
                }
            }
        })
    }

    // Media viewer now uses Compose's pager; toolbar subtitle updated by Compose if required

    private fun isMediaAvailable(fileName: String): Boolean = getMediaFile(fileName).exists()

    private fun getMediaFile(fileName: String): File {
        val mediaFolder = File(android.os.Environment.getExternalStorageDirectory(), Constants.MEDIA_FOLDER)
        return File(mediaFolder, fileName)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEDIA_FILES = "com.medicalquiz.app.extra.MEDIA_FILES"
        const val EXTRA_START_INDEX = "com.medicalquiz.app.extra.START_INDEX"
    }
}
