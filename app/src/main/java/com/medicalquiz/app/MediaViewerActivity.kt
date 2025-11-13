package com.medicalquiz.app

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import com.medicalquiz.app.databinding.ActivityMediaViewerBinding
import com.medicalquiz.app.utils.WebViewRenderer
import java.io.File

class MediaViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaViewerBinding
    private var mediaFiles = listOf<String>()
    private var currentIndex = 0
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Media Viewer"

        // Get media files from intent
        val allMediaFiles = intent.getStringArrayListExtra("MEDIA_FILES") ?: arrayListOf()
        val startIndex = intent.getIntExtra("START_INDEX", 0)

        // Filter to only available media files
        mediaFiles = allMediaFiles.filter { isMediaAvailable(it) }
        
        if (mediaFiles.isEmpty()) {
            finish()
            return
        }

        // Find the actual index in filtered list
        currentIndex = if (startIndex < allMediaFiles.size) {
            val requestedFile = allMediaFiles[startIndex]
            mediaFiles.indexOf(requestedFile).takeIf { it >= 0 } ?: 0
        } else {
            0
        }

        setupListeners()
        displayMedia()
    }

    private fun setupListeners() {
        binding.buttonPrevious.setOnClickListener {
            if (currentIndex > 0) {
                releaseMediaPlayer()
                currentIndex--
                displayMedia()
            }
        }

        binding.buttonNext.setOnClickListener {
            if (currentIndex < mediaFiles.size - 1) {
                releaseMediaPlayer()
                currentIndex++
                displayMedia()
            }
        }
    }

    private fun displayMedia() {
        val fileName = mediaFiles[currentIndex]
        val file = getMediaFile(fileName)

        // Update navigation buttons
        binding.buttonPrevious.isEnabled = currentIndex > 0
        binding.buttonNext.isEnabled = currentIndex < mediaFiles.size - 1
        
        // Update counter
        binding.textViewCounter.text = "${currentIndex + 1} / ${mediaFiles.size}"
        supportActionBar?.subtitle = fileName

        // Hide all views
        binding.imageView.visibility = View.GONE
        binding.videoView.visibility = View.GONE
        binding.audioContainer.visibility = View.GONE
        binding.webViewHtml.visibility = View.GONE

        if (file == null || !file.exists()) {
            // Skip to next available file or close if none left
            if (currentIndex < mediaFiles.size - 1) {
                currentIndex++
                displayMedia()
            } else if (currentIndex > 0) {
                currentIndex--
                displayMedia()
            } else {
                finish()
            }
            return
        }

        when (getMediaType(fileName)) {
            MediaType.IMAGE -> displayImage(file)
            MediaType.VIDEO -> displayVideo(file)
            MediaType.AUDIO -> displayAudio(file)
            MediaType.HTML -> displayHtml(file)
            MediaType.UNKNOWN -> {
                // Skip unsupported file types
                if (currentIndex < mediaFiles.size - 1) {
                    currentIndex++
                    displayMedia()
                } else if (currentIndex > 0) {
                    currentIndex--
                    displayMedia()
                } else {
                    finish()
                }
            }
        }
    }

    private fun displayImage(file: File) {
        binding.imageView.visibility = View.VISIBLE
        binding.imageView.setImageURI(Uri.fromFile(file))
    }

    private fun displayVideo(file: File) {
        binding.videoView.visibility = View.VISIBLE
        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        
        binding.videoView.setMediaController(mediaController)
        binding.videoView.setVideoURI(Uri.fromFile(file))
        binding.videoView.requestFocus()
        binding.videoView.start()
    }

    private fun displayAudio(file: File) {
        binding.audioContainer.visibility = View.VISIBLE
        binding.textViewAudioName.text = file.name
        
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
        }

        val duration = mediaPlayer?.duration ?: 0
        val minutes = duration / 1000 / 60
        val seconds = (duration / 1000) % 60
        binding.textViewDuration.text = String.format("%d:%02d", minutes, seconds)

        binding.buttonPlayPause.setOnClickListener {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    binding.buttonPlayPause.text = "▶ Play"
                } else {
                    player.start()
                    binding.buttonPlayPause.text = "⏸ Pause"
                }
            }
        }

        binding.buttonStop.setOnClickListener {
            mediaPlayer?.let { player ->
                player.seekTo(0)
                player.pause()
                binding.buttonPlayPause.text = "▶ Play"
            }
        }
    }

    private fun displayHtml(file: File) {
        binding.webViewHtml.visibility = View.VISIBLE
        WebViewRenderer.setupWebView(binding.webViewHtml)
        
        val htmlContent = file.readText()
        WebViewRenderer.loadContent(this, binding.webViewHtml, htmlContent)
    }

    private fun getMediaFile(fileName: String): File? {
        val mediaFolder = File(Environment.getExternalStorageDirectory(), "MedicalQuiz/media")
        return File(mediaFolder, fileName)
    }

    private fun isMediaAvailable(fileName: String): Boolean {
        val file = getMediaFile(fileName)
        return file != null && file.exists()
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

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    enum class MediaType {
        IMAGE, VIDEO, AUDIO, HTML, UNKNOWN
    }
}
