package com.medicalquiz.app.shared.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
actual fun AudioPlayer(
    filePath: String,
    modifier: Modifier,
    isActivePage: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playWhenReady by remember { mutableStateOf(false) }
    
    val exoPlayer = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse("file://$filePath"))
            setMediaItem(mediaItem)
            prepare()
            this.playWhenReady = playWhenReady
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Handle lifecycle - pause when backgrounded
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    playWhenReady = exoPlayer.playWhenReady
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.playWhenReady = playWhenReady
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // Pause when not on active page
    LaunchedEffect(isActivePage) {
        if (!isActivePage && exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }

    AndroidView(
        factory = { _ ->
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
                controllerShowTimeoutMs = 0 // Always show controller for audio
                controllerHideOnTouch = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    )
}
