package com.medicalquiz.app

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import coil.load
import com.medicalquiz.app.databinding.ActivityMediaViewerBinding
import com.medicalquiz.app.utils.WebViewRenderer
import com.medicalquiz.app.utils.launchCatching
import kotlinx.coroutines.Dispatchers
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
        WebViewRenderer.setupWebView(binding.webViewHtml)

        val allMediaFiles = intent.getStringArrayListExtra(EXTRA_MEDIA_FILES) ?: arrayListOf()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

        mediaFiles = allMediaFiles.filter(::isMediaAvailable)
        if (mediaFiles.isEmpty()) {
            finish()
            return
        }

        currentIndex = mediaFiles.indexOf(allMediaFiles.getOrNull(startIndex)).takeIf { it != -1 } ?: 0

        setupListeners()
        displayMedia()
    }

    private fun setupListeners() {
        binding.buttonPrevious.setOnClickListener {
            if (currentIndex > 0) navigateToIndex(currentIndex - 1)
        }

        binding.buttonNext.setOnClickListener {
            if (currentIndex < mediaFiles.lastIndex) navigateToIndex(currentIndex + 1)
        }
    }

    private fun navigateToIndex(index: Int) {
        if (index in mediaFiles.indices) {
            releaseMediaPlayer()
            currentIndex = index
            displayMedia()
        }
    }

    private fun displayMedia() {
        releaseMediaPlayer()
        val fileName = mediaFiles[currentIndex]
        val file = getMediaFile(fileName)

        binding.buttonPrevious.isEnabled = currentIndex > 0
        binding.buttonNext.isEnabled = currentIndex < mediaFiles.lastIndex

        binding.textViewCounter.text = "${currentIndex + 1} / ${mediaFiles.size}"
        supportActionBar?.subtitle = fileName

        setAllContentInvisible()
        if (file == null || !file.exists()) {
            skipOrFinish()
            return
        }

        when (getMediaType(fileName)) {
            MediaType.IMAGE -> displayImage(file)
            MediaType.VIDEO -> displayVideo(file)
            MediaType.AUDIO -> displayAudio(file)
            MediaType.HTML -> displayHtml(file)
            MediaType.UNKNOWN -> skipOrFinish()
        }
    }

    private fun setAllContentInvisible() {
        binding.imageView.isVisible = false
        binding.videoView.isVisible = false
        binding.audioContainer.isVisible = false
        binding.webViewHtml.isVisible = false
    }

    private fun skipOrFinish() {
        val hasNext = currentIndex < mediaFiles.lastIndex
        val hasPrevious = currentIndex > 0
        when {
            hasNext -> navigateToIndex(currentIndex + 1)
            hasPrevious -> navigateToIndex(currentIndex - 1)
            else -> finish()
        }
    }

    private fun displayImage(file: File) {
        binding.imageView.isVisible = true
        binding.imageView.load(file) {
            crossfade(true)
        }
    }

    private fun displayVideo(file: File) {
        binding.videoView.isVisible = true
        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        
        binding.videoView.setMediaController(mediaController)
        binding.videoView.setVideoURI(Uri.fromFile(file))
        binding.videoView.requestFocus()
        binding.videoView.start()
    }

    private fun displayAudio(file: File) {
        binding.audioContainer.isVisible = true
        binding.textViewAudioName.text = file.name
        binding.textViewDuration.text = "--:--"
        binding.buttonPlayPause.isEnabled = false
        binding.buttonPlayPause.text = "▶ Play"

        mediaPlayer = MediaPlayer().apply {
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
                player.pause()
                player.seekTo(0)
                binding.buttonPlayPause.text = "▶ Play"
            }
        }
    }

    private fun displayHtml(file: File) {
        binding.webViewHtml.isVisible = true
        launchCatching(
            dispatcher = Dispatchers.IO,
            block = { file.readText() },
            onSuccess = { html ->
                WebViewRenderer.loadContent(this@MediaViewerActivity, binding.webViewHtml, html)
            }
        )
    }

    private fun getMediaFile(fileName: String): File? {
        val mediaFolder = File(Environment.getExternalStorageDirectory(), MEDIA_FOLDER)
        return File(mediaFolder, fileName)
    }

    private fun isMediaAvailable(fileName: String): Boolean = getMediaFile(fileName).exists()

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

    private fun formatDuration(durationMs: Int): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
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

    companion object {
        const val EXTRA_MEDIA_FILES = "com.medicalquiz.app.extra.MEDIA_FILES"
        const val EXTRA_START_INDEX = "com.medicalquiz.app.extra.START_INDEX"
        private const val MEDIA_FOLDER = "MedicalQuiz/media"
    }
}
