package com.medicalquiz.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.medicalquiz.app.databinding.ActivityMediaViewerBinding
import java.io.File

class MediaViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaViewerBinding
    private lateinit var adapter: MediaViewerAdapter
    private var mediaFiles = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Media Viewer"

        val allMediaFiles = intent.getStringArrayListExtra(EXTRA_MEDIA_FILES) ?: arrayListOf()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

        mediaFiles = allMediaFiles.filter(::isMediaAvailable)
        if (mediaFiles.isEmpty()) {
            finish()
            return
        }

        val actualStartIndex = mediaFiles.indexOf(allMediaFiles.getOrNull(startIndex)).takeIf { it != -1 } ?: 0

        setupViewPager(actualStartIndex)
    }

    private fun setupViewPager(startIndex: Int) {
        adapter = MediaViewerAdapter(mediaFiles, this)
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startIndex, false)

        // Update counter when page changes
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateCounter(position)
                updateToolbarSubtitle(position)
            }
        })

        // Set initial state
        updateCounter(startIndex)
        updateToolbarSubtitle(startIndex)
    }

    private fun updateCounter(position: Int) {
        binding.textViewCounter.text = "${position + 1} / ${mediaFiles.size}"
    }

    private fun updateToolbarSubtitle(position: Int) {
        supportActionBar?.subtitle = mediaFiles.getOrNull(position) ?: ""
    }

    private fun isMediaAvailable(fileName: String): Boolean = getMediaFile(fileName).exists()

    private fun getMediaFile(fileName: String): File {
        val mediaFolder = File(android.os.Environment.getExternalStorageDirectory(), MEDIA_FOLDER)
        return File(mediaFolder, fileName)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_MEDIA_FILES = "com.medicalquiz.app.extra.MEDIA_FILES"
        const val EXTRA_START_INDEX = "com.medicalquiz.app.extra.START_INDEX"
        private const val MEDIA_FOLDER = "MedicalQuiz/media"
    }
}
