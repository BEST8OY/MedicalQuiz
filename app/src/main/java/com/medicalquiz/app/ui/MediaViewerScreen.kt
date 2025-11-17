package com.medicalquiz.app.ui

import androidx.activity.compose.BackHandler
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.medicalquiz.app.MediaType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.widget.MediaController
import com.medicalquiz.app.utils.WebViewRenderer
import java.io.File

@Composable
fun MediaViewerScreen(mediaFiles: List<String>, startIndex: Int) {
    val pagerState = rememberPagerState(initialPage = startIndex)
    var currentIndex by remember { mutableStateOf(startIndex) }

    BackHandler { /* Back handled by hosting activity */ }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(mediaFiles.size, state = pagerState) { page ->
            currentIndex = page
            val file = mediaFiles[page]
            when (getMediaType(file)) {
                MediaType.IMAGE -> {
                    AsyncImage(model = file, contentDescription = "Media $page", modifier = Modifier.fillMaxSize())
                }
                MediaType.VIDEO -> {
                    val context = LocalContext.current
                    AndroidView(factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.fromFile(File(Environment.getExternalStorageDirectory(), "${com.medicalquiz.app.Constants.MEDIA_FOLDER}/$file")))
                            val mc = MediaController(ctx)
                            mc.setAnchorView(this)
                            setMediaController(mc)
                            setOnPreparedListener { start() }
                        }
                    }, modifier = Modifier.fillMaxSize())
                }
                MediaType.AUDIO -> {
                    val context = LocalContext.current
                    val mediaFile = File(Environment.getExternalStorageDirectory(), "${com.medicalquiz.app.Constants.MEDIA_FOLDER}/$file")
                    val mediaPlayer = remember { MediaPlayer() }
                    DisposableEffect(mediaFile) {
                        mediaPlayer.setDataSource(mediaFile.absolutePath)
                        mediaPlayer.prepare()
                        onDispose {
                            mediaPlayer.stop()
                            mediaPlayer.release()
                        }
                    }

                    // Simple controls
                    Column {
                        Text(text = mediaFile.name)
                        Button(onClick = {
                            if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.start()
                        }) {
                            Text(if (mediaPlayer.isPlaying) "Pause" else "Play")
                        }
                    }
                }
                MediaType.HTML -> {
                    val htmlFile = File(Environment.getExternalStorageDirectory(), "${com.medicalquiz.app.Constants.MEDIA_FOLDER}/$file")
                    AndroidView(factory = { ctx ->
                        val wv = android.webkit.WebView(ctx)
                        WebViewRenderer.setupWebView(wv)
                        WebViewRenderer.loadContent(ctx, wv, htmlFile.readText())
                        wv
                    }, modifier = Modifier.fillMaxSize())
                }
                MediaType.UNKNOWN -> {
                    Text(text = "Unsupported media type")
                }
            }
        }

        // Counter overlay
        Text(text = "${currentIndex + 1} / ${mediaFiles.size}")
    }
}

private fun getMediaType(fileName: String): com.medicalquiz.app.MediaType {
    return when (fileName.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> com.medicalquiz.app.MediaType.IMAGE
        "mp4", "avi", "mkv", "mov", "webm", "3gp" -> com.medicalquiz.app.MediaType.VIDEO
        "mp3", "wav", "ogg", "m4a", "aac", "flac" -> com.medicalquiz.app.MediaType.AUDIO
        "html", "htm" -> com.medicalquiz.app.MediaType.HTML
        else -> com.medicalquiz.app.MediaType.UNKNOWN
    }
}
