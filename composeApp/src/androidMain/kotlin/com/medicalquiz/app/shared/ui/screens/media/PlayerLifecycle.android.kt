package com.medicalquiz.app.shared.ui.screens.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer

@Composable
internal fun ExoPlayerLifecycleEffects(
    exoPlayer: ExoPlayer,
    isActivePage: Boolean,
    playWhenReady: Boolean,
    onPlayWhenReadyChange: (Boolean) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LifecycleResumeEffect(lifecycleOwner, exoPlayer) {
        exoPlayer.playWhenReady = playWhenReady

        onPauseOrDispose {
            onPlayWhenReadyChange(exoPlayer.playWhenReady)
            exoPlayer.pause()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(isActivePage, exoPlayer) {
        if (!isActivePage && exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }
}
