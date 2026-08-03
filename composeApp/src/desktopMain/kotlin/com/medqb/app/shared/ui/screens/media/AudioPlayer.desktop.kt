package com.medqb.app.shared.ui.screens.media

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.platform.VlcDiscovery
import com.medqb.app.shared.ui.theme.IconSize
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Layout
import com.medqb.app.shared.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@Composable
actual fun AudioPlayer(
    filePath: String,
    modifier: Modifier,
    isActivePage: Boolean
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableIntStateOf(100) }
    var audioPlayer by remember { mutableStateOf<AudioPlayerComponent?>(null) }
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

    // Initialize audio player after VLC is discovered
    LaunchedEffect(filePath, vlcDiscovered) {
        if (!vlcDiscovered || errorMessage != null) return@LaunchedEffect
        
        try {
            audioPlayer?.mediaPlayer()?.controls()?.stop()
            audioPlayer?.release()
            
            val component = AudioPlayerComponent()
            audioPlayer = component
            
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
                    currentTime = 0L
                }
            })
            
            // Prepare media but don't autoplay
            component.mediaPlayer().media().prepare(File(filePath).absolutePath)
        } catch (e: Exception) {
            errorMessage = "Failed to load audio: ${e.message}"
            println("Audio player error: ${e.message}")
        }
    }

    // Update time periodically
    LaunchedEffect(isPlaying) {
        while (isPlaying && isActive) {
            delay(100.milliseconds)
            audioPlayer?.mediaPlayer()?.status()?.time()?.let {
                currentTime = it
            }
        }
    }

    // Pause when not on active page
    LaunchedEffect(isActivePage) {
        if (!isActivePage && isPlaying) {
            audioPlayer?.mediaPlayer()?.controls()?.pause()
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer?.mediaPlayer()?.controls()?.stop()
            audioPlayer?.release()
            audioPlayer = null
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (errorMessage != null) {
            Column(
                modifier = Modifier.padding(Inset.ExtraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(Modifier.height(Spacing.Medium))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(Spacing.Medium))
                Button(
                    onClick = {
                        if (VlcDiscovery.retry()) {
                            errorMessage = null
                            vlcDiscovered = true
                        }
                    }
                ) {
                    Text("Retry")
                }
            }
        } else {
            // Audio player UI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Inset.ExtraLarge),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.Large),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    // File name
                    Text(
                        text = File(filePath).name,
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Progress bar
                    if (duration > 0) {
                        LinearProgressIndicator(
                            progress = { (currentTime.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Time display
                    if (duration > 0) {
                        Text(
                            text = "${formatTime(currentTime)} / ${formatTime(duration)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause button
                        IconButton(
                            onClick = {
                                audioPlayer?.mediaPlayer()?.let { player ->
                                    if (isPlaying) player.controls().pause() else player.controls().play()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                                modifier = Modifier.size(Layout.MinTouchTarget)
                            )
                        }

                        // Volume control
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(start = Spacing.Medium)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume control",
                                modifier = Modifier.size(IconSize.Large)
                            )
                            Spacer(Modifier.width(Spacing.Small))
                            Slider(
                                value = volume.toFloat(),
                                onValueChange = { newVolume ->
                                    volume = newVolume.toInt()
                                    audioPlayer?.mediaPlayer()?.audio()?.setVolume(volume)
                                },
                                valueRange = 0f..100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
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
