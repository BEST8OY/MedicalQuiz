package com.medqb.app.shared.ui.screens.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
@Composable
actual fun AudioPlayer(
    filePath: String,
    modifier: Modifier,
    isActivePage: Boolean
) {
    val context = LocalContext.current
    var playWhenReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse("file://$filePath"))
            setMediaItem(mediaItem)
            prepare()
            this.playWhenReady = playWhenReady
            repeatMode = Player.REPEAT_MODE_OFF

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        duration = this@apply.duration.coerceAtLeast(0L)
                    } else if (playbackState == Player.STATE_ENDED) {
                        currentPosition = this@apply.duration.coerceAtLeast(0L)
                        isPlaying = false
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
        }
    }

    ExoPlayerLifecycleEffects(
        exoPlayer = exoPlayer,
        isActivePage = isActivePage,
        playWhenReady = playWhenReady,
        onPlayWhenReadyChange = { playWhenReady = it },
    )

    // Poll playback position for Compose-driven controls.
    LaunchedEffect(exoPlayer, isSeeking) {
        while (true) {
            if (!isSeeking) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    val displayedPosition = if (isSeeking) seekPosition else currentPosition
    val sliderProgress = if (duration > 0L) {
        (displayedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Slider(
            value = sliderProgress,
            onValueChange = { value ->
                if (duration <= 0L) return@Slider
                isSeeking = true
                seekPosition = (duration * value).toLong().coerceIn(0L, duration)
            },
            onValueChangeFinished = {
                if (!isSeeking) return@Slider
                exoPlayer.seekTo(seekPosition)
                currentPosition = seekPosition
                isSeeking = false
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(displayedPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isPlaying) {
                    exoPlayer.pause()
                    playWhenReady = false
                } else {
                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(0)
                    }
                    exoPlayer.play()
                    playWhenReady = true
                }
            },
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isPlaying) "Pause" else "Play")
        }
    }
}

private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000 / 60) % 60
    val hours = millis / 1000 / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
