package com.medicalquiz.app.shared.ui.screens.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayer(
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
            // Force initial frame to render by seeking to start
            // This workaround fixes SurfaceView rendering issues in Compose
            seekTo(0)
        }
    }

    // Handle lifecycle - pause when backgrounded
    LifecycleResumeEffect(lifecycleOwner, exoPlayer) {
        // ON_RESUME: restore play state
        exoPlayer.playWhenReady = playWhenReady

        onPauseOrDispose {
            // ON_PAUSE: save state and pause
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.pause()
        }
    }

    // Release player when leaving composition
    DisposableEffect(Unit) {
        onDispose {
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
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                // Enable Compose surface sync workaround for Android 14+ (API 34+)
                // See: https://developer.android.com/media/media3/ui/surface
                setEnableComposeSurfaceSyncWorkaround(true)
                // Set the player
                player = exoPlayer
            }
        },
        update = { playerView ->
            // Ensure player is attached
            playerView.player = exoPlayer
        },
        modifier = modifier
    )
}
