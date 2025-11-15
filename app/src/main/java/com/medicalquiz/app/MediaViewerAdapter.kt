package com.medicalquiz.app

import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.medicalquiz.app.databinding.ItemMediaViewerBinding
import com.medicalquiz.app.utils.WebViewRenderer
import com.medicalquiz.app.utils.launchCatching
import kotlinx.coroutines.Dispatchers
import java.io.File

class MediaViewerAdapter(
    private val mediaFiles: List<String>,
    private val activity: MediaViewerActivity
) : RecyclerView.Adapter<MediaViewerAdapter.MediaViewHolder>() {

    private val mediaPlayers = mutableMapOf<Int, MediaPlayer?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaViewerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(mediaFiles[position], position)
    }

    override fun getItemCount(): Int = mediaFiles.size

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        // Release media player when view is recycled
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            mediaPlayers[position]?.let { player ->
                player.release()
                mediaPlayers.remove(position)
            }
        }
    }

    inner class MediaViewHolder(private val binding: ItemMediaViewerBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(fileName: String, position: Int) {
            val file = getMediaFile(fileName)
            val mediaType = getMediaType(fileName)

            // Hide all views first
            setAllContentInvisible()

            if (!file.exists()) {
                // Show error state - could add a placeholder here
                return
            }

            when (mediaType) {
                MediaType.IMAGE -> displayImage(file)
                MediaType.VIDEO -> displayVideo(file)
                MediaType.AUDIO -> displayAudio(file, position)
                MediaType.HTML -> displayHtml(file)
                MediaType.UNKNOWN -> {
                    // Could show an error message
                }
            }
        }

        private fun setAllContentInvisible() {
            binding.imageView.isVisible = false
            binding.videoView.isVisible = false
            binding.audioContainer.isVisible = false
            binding.webViewHtml.isVisible = false
        }

        private fun displayImage(file: File) {
            binding.imageView.isVisible = true
            binding.imageView.load(file) {
                crossfade(true)
            }
        }

        private fun displayVideo(file: File) {
            binding.videoView.isVisible = true
            val mediaController = MediaController(activity)
            mediaController.setAnchorView(binding.videoView)

            binding.videoView.setMediaController(mediaController)
            binding.videoView.setVideoURI(Uri.fromFile(file))
            binding.videoView.requestFocus()
            binding.videoView.start()
        }

        private fun displayAudio(file: File, position: Int) {
            binding.audioContainer.isVisible = true
            binding.textViewAudioName.text = file.name
            binding.textViewDuration.text = "--:--"
            binding.buttonPlayPause.isEnabled = false
            binding.buttonPlayPause.text = "▶ Play"

            val mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { player ->
                    binding.buttonPlayPause.isEnabled = true
                    binding.textViewDuration.text = formatDuration(player.duration)
                }
                setOnCompletionListener {
                    binding.buttonPlayPause.text = "▶ Play"
                }
                prepareAsync()
            }

            mediaPlayers[position] = mediaPlayer

            binding.buttonPlayPause.setOnClickListener {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    binding.buttonPlayPause.text = "▶ Play"
                } else {
                    mediaPlayer.start()
                    binding.buttonPlayPause.text = "⏸ Pause"
                }
            }

            binding.buttonStop.setOnClickListener {
                mediaPlayer.pause()
                mediaPlayer.seekTo(0)
                binding.buttonPlayPause.text = "▶ Play"
            }
        }

        private fun displayHtml(file: File) {
            binding.webViewHtml.isVisible = true
            WebViewRenderer.setupWebView(binding.webViewHtml)
            activity.launchCatching(
                dispatcher = Dispatchers.IO,
                block = { file.readText() },
                onSuccess = { html ->
                    WebViewRenderer.loadContent(activity, binding.webViewHtml, html)
                }
            )
        }

        private fun getMediaFile(fileName: String): File {
            val mediaFolder = File(Environment.getExternalStorageDirectory(), MEDIA_FOLDER)
            return File(mediaFolder, fileName)
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

        private fun formatDuration(durationMs: Int): String {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }

    companion object {
        private const val MEDIA_FOLDER = "MedicalQuiz/media"
    }
}

enum class MediaType {
    IMAGE, VIDEO, AUDIO, HTML, UNKNOWN
}