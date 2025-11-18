package com.medicalquiz.app.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.medicalquiz.app.MediaType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.widget.MediaController
import com.medicalquiz.app.utils.WebViewRenderer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(mediaFiles: List<String>, startIndex: Int) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { mediaFiles.size })
    var currentIndex by remember { mutableStateOf(startIndex) }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Media Viewer",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentIndex + 1} / ${mediaFiles.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { /* Handled by activity back press */ }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                currentIndex = page
                val file = mediaFiles[page]
                when (getMediaType(file)) {
                    MediaType.IMAGE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Media ${page + 1}",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                        var isPlaying by remember { mutableStateOf(false) }
                        var currentPosition by remember { mutableStateOf(0) }
                        var duration by remember { mutableStateOf(0) }
                        
                        DisposableEffect(mediaFile) {
                            try {
                                mediaPlayer.setDataSource(mediaFile.absolutePath)
                                mediaPlayer.prepare()
                                duration = mediaPlayer.duration
                                mediaPlayer.setOnCompletionListener { isPlaying = false }
                            } catch (e: Exception) {
                                android.util.Log.e("MediaViewerScreen", "Error preparing audio", e)
                            }
                            onDispose {
                                mediaPlayer.stop()
                                mediaPlayer.release()
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(24.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = mediaFile.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            
                            Button(
                                onClick = {
                                    if (isPlaying) {
                                        mediaPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        mediaPlayer.start()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier.padding(bottom = 24.dp)
                            ) {
                                Text(if (isPlaying) "Pause" else "Play")
                            }
                            
                            if (duration > 0) {
                                LinearProgressIndicator(
                                    progress = { currentPosition.toFloat() / duration },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                )
                                Text(
                                    text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Unsupported media type",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
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

private fun formatTime(milliseconds: Int): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = milliseconds / (1000 * 60 * 60)
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
