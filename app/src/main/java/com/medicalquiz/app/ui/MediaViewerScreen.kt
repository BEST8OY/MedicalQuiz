package com.medicalquiz.app.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import android.widget.MediaController
import android.widget.VideoView
import coil3.compose.AsyncImage
import coil3.decode.SvgDecoder
import coil3.request.ImageRequest
import com.medicalquiz.app.Constants
import com.medicalquiz.app.MediaType
import com.medicalquiz.app.utils.WebViewRenderer
import java.io.File

private const val TAG = "MediaViewerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(mediaFiles: List<String>, startIndex: Int) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { mediaFiles.size })
    var currentIndex by remember { mutableStateOf(startIndex) }
    var userScrollEnabled by remember { mutableStateOf(true) }
    
    Scaffold(
        topBar = {
            MediaViewerTopBar(currentIndex, mediaFiles.size)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = userScrollEnabled,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                currentIndex = page
                val file = mediaFiles[page]
                MediaContent(
                    file = file,
                    onZoomStateChange = { isZoomed ->
                        userScrollEnabled = !isZoomed
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaViewerTopBar(currentIndex: Int, totalFiles: Int) {
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
                    text = "${currentIndex + 1} / $totalFiles",
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun MediaContent(
    file: String,
    onZoomStateChange: (Boolean) -> Unit
) {
    LaunchedEffect(file) {
        onZoomStateChange(false)
    }
    when (getMediaType(file)) {
        MediaType.IMAGE -> ImageContent(file, onZoomStateChange)
        MediaType.VIDEO -> VideoContent(file)
        MediaType.AUDIO -> AudioContent(file)
        MediaType.HTML -> HtmlContent(file)
        MediaType.UNKNOWN -> UnsupportedContent()
    }
}

@Composable
private fun ImageContent(fileName: String, onZoomStateChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val imageFile = File(
        Environment.getExternalStorageDirectory(),
        "${Constants.MEDIA_FOLDER}/$fileName"
    )
    val overlayFile = getOverlayFile(fileName)
    val overlayAvailable = overlayFile?.exists() == true
    val showOverlayControls = overlayAvailable && fileName.startsWith("big_", ignoreCase = true)
    var isOverlayVisible by rememberSaveable(fileName) { mutableStateOf(showOverlayControls) }
    var scale by rememberSaveable(fileName) { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val minScale = 1f
    val maxScale = 5f
    val overlayRequest = remember(overlayFile?.absolutePath) {
        overlayFile?.takeIf { showOverlayControls }?.let {
            ImageRequest.Builder(context)
                .data(it)
                .decoderFactory(SvgDecoder.Factory())
                .build()
        }
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        scale = newScale
        offset = if (newScale <= 1.01f) {
            Offset.Zero
        } else {
            offset + panChange
        }
    }

    LaunchedEffect(fileName) {
        scale = 1f
        offset = Offset.Zero
    }

    val zoomModifier = Modifier
        .fillMaxSize()
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y
        )
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                }
            )
        }
        .transformable(transformableState)

    val isZoomed = scale > 1.01f
    LaunchedEffect(isZoomed) {
        onZoomStateChange(isZoomed)
    }
    
    // Log file info
    android.util.Log.d(
        TAG,
        "IMAGE: file=$fileName, path=${imageFile.absolutePath}, exists=${imageFile.exists()}, canRead=${imageFile.canRead()}, length=${imageFile.length()}, overlay=${overlayFile?.absolutePath}, overlayExists=$overlayAvailable"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (imageFile.exists()) {
            Box(modifier = zoomModifier) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = "Media image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                if (showOverlayControls && isOverlayVisible && overlayRequest != null) {
                    AsyncImage(
                        model = overlayRequest,
                        contentDescription = "Overlay image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }

            if (showOverlayControls) {
                FilledTonalIconButton(
                    onClick = { isOverlayVisible = !isOverlayVisible },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = if (isOverlayVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (isOverlayVisible) "Hide overlay" else "Show overlay"
                    )
                }
            }
        } else {
            ErrorMessage("Image not found:\n${imageFile.absolutePath}")
        }
    }
}

private fun getOverlayFile(fileName: String): File? {
    if (!fileName.startsWith("big_", ignoreCase = true) || !fileName.contains('.')) {
        return null
    }
    val baseName = fileName.substringBeforeLast('.')
    val overlayName = "$baseName.svg"
    return File(
        Environment.getExternalStorageDirectory(),
        "${Constants.MEDIA_FOLDER}/$overlayName"
    )
}

@Composable
private fun VideoContent(fileName: String) {
    val videoFile = File(
        Environment.getExternalStorageDirectory(),
        "${Constants.MEDIA_FOLDER}/$fileName"
    )
    
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.fromFile(videoFile))
                val mc = MediaController(ctx)
                mc.setAnchorView(this)
                setMediaController(mc)
                setOnPreparedListener { start() }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun AudioContent(fileName: String) {
    val mediaFile = File(
        Environment.getExternalStorageDirectory(),
        "${Constants.MEDIA_FOLDER}/$fileName"
    )
    val mediaPlayer = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    
    DisposableEffect(mediaFile) {
        try {
            mediaPlayer.setDataSource(mediaFile.absolutePath)
            mediaPlayer.prepare()
            duration = mediaPlayer.duration
            mediaPlayer.setOnCompletionListener { isPlaying = false }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error preparing audio", e)
        }
        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error releasing media player", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
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
                progress = { mediaPlayer.currentPosition.toFloat() / duration },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
            Text(
                text = "${formatTime(mediaPlayer.currentPosition)} / ${formatTime(duration)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HtmlContent(fileName: String) {
    val htmlFile = File(
        Environment.getExternalStorageDirectory(),
        "${Constants.MEDIA_FOLDER}/$fileName"
    )
    
    AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                WebViewRenderer.setupWebView(this)
                WebViewRenderer.loadContent(ctx, this, htmlFile.readText())
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun UnsupportedContent() {
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

@Composable
private fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun getMediaType(fileName: String): MediaType {
    return when (fileName.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> MediaType.IMAGE
        "mp4", "avi", "mkv", "mov", "webm", "3gp" -> MediaType.VIDEO
        "mp3", "wav", "ogg", "m4a", "aac", "flac" -> MediaType.AUDIO
        "html", "htm" -> MediaType.HTML
        else -> MediaType.UNKNOWN
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
