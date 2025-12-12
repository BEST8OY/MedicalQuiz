package com.medicalquiz.app.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@Composable
actual fun AudioPlayer(
    filePath: String,
    modifier: Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var volume by remember { mutableIntStateOf(100) }
    var audioPlayer by remember { mutableStateOf<AudioPlayerComponent?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var vlcDiscovered by remember { mutableStateOf(false) }

    // Initialize VLC once
    LaunchedEffect(Unit) {
        try {
            vlcDiscovered = NativeDiscovery().discover()
            if (!vlcDiscovered) {
                errorMessage = "VLC not found. Please install VLC media player."
            }
        } catch (e: Exception) {
            errorMessage = "VLC not found. Please install VLC media player."
            println("VLC discovery failed: ${e.message}")
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
            
            component.mediaPlayer().media().play(File(filePath).absolutePath)
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
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        } else {
            // Audio player UI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        // Volume control
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(start = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Volume",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
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
