package com.medqb.app.shared.ui.screens.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.platform.VlcDiscovery
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.awt.Component
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@Composable
actual fun VideoPlayer(
    filePath: String,
    modifier: Modifier,
    isActivePage: Boolean
) {
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableIntStateOf(100) }
    var mediaPlayerComponent: CallbackMediaPlayerComponent? by remember { mutableStateOf(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var vlcDiscovered by remember { mutableStateOf(false) }

    // Initialize VLC using cached discovery
    LaunchedEffect(Unit) {
        vlcDiscovered = VlcDiscovery.isAvailable()
        if (!vlcDiscovered) {
            errorMessage = "VLC not found. Please install VLC media player."
            println("VLC discovery failed: ${VlcDiscovery.getLastError()}")
        }
    }

    // Update time periodically using produceState for proper lifecycle management
    val currentTime by produceState(initialValue = 0L, isPlaying, mediaPlayerComponent) {
        while (isActive && isPlaying) {
            delay(100.milliseconds)
            mediaPlayerComponent?.mediaPlayer()?.status()?.time()?.let {
                value = it
            }
        }
    }

    // Pause when not on active page
    LaunchedEffect(isActivePage) {
        if (!isActivePage && isPlaying) {
            mediaPlayerComponent?.mediaPlayer()?.controls()?.pause()
        }
    }

    // Cleanup and restart when filePath changes
    DisposableEffect(filePath) {
        onDispose {
            mediaPlayerComponent?.let { component ->
                try {
                    component.mediaPlayer().controls().stop()
                    component.release()
                } catch (e: Exception) {
                    println("Error cleaning up video player: ${e.message}")
                }
            }
            mediaPlayerComponent = null
            isPlaying = false
            duration = 0L
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (errorMessage != null) {
            VlcErrorState(
                message = errorMessage!!,
                onRetry = {
                    if (VlcDiscovery.retry()) {
                        errorMessage = null
                        vlcDiscovered = true
                    }
                },
            )
        } else if (vlcDiscovered) {
            SwingPanel(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                factory = {
                    val component = CallbackMediaPlayerComponent()
                    mediaPlayerComponent = component
                    
                    component.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                        override fun playing(mediaPlayer: MediaPlayer) {
                            isPlaying = true
                            duration = mediaPlayer.status().length()
                        }

                        override fun paused(mediaPlayer: MediaPlayer) {
                            isPlaying = false
                        }

                        override fun stopped(mediaPlayer: MediaPlayer) {
                            isPlaying = false
                        }

                        override fun finished(mediaPlayer: MediaPlayer) {
                            isPlaying = false
                        }
                    })
                    
                    // Prepare media but don't autoplay
                    component.mediaPlayer().media().prepare(File(filePath).absolutePath)
                    component as Component
                }
            )

            // Control overlay
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                // Progress bar
                if (duration > 0) {
                    LinearProgressIndicator(
                        progress = { (currentTime.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Pause button
                    IconButton(
                        onClick = {
                            mediaPlayerComponent?.mediaPlayer()?.let { player ->
                                if (isPlaying) player.controls().pause() else player.controls().play()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = if (isPlaying) "Pause video" else "Play video",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Time display
                    if (duration > 0) {
                        Text(
                            text = "${formatMediaDuration(currentTime)} / ${formatMediaDuration(duration)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Volume control
                    DesktopVolumeControl(
                        volume = volume,
                        onVolumeChange = { newVolume ->
                            volume = newVolume
                            mediaPlayerComponent?.mediaPlayer()?.audio()?.setVolume(volume)
                        },
                        iconSize = 20.dp,
                        modifier = Modifier.width(150.dp),
                    )
                }
            }
            }
        } else {
            // Loading state while VLC is being discovered
            VlcLoadingState()
        }
    }
}
