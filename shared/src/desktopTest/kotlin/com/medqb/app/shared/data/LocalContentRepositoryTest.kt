package com.medqb.app.shared.data

import com.medqb.app.shared.platform.StorageProvider
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalContentRepositoryTest {

    private val repository = LocalContentRepository()

    @Test
    fun safeMediaFilePathRejectsTraversalAndInvalidNames() {
        assertNull(repository.safeMediaFilePath(""))
        assertNull(repository.safeMediaFilePath("   "))
        assertNull(repository.safeMediaFilePath("."))
        assertNull(repository.safeMediaFilePath(".."))
        assertNull(repository.safeMediaFilePath("../secret.txt"))
        assertNull(repository.safeMediaFilePath("folder/../../secret.txt"))

        val safe = repository.safeMediaFilePath("valid_image.png")
        assertNotNull(safe)
        assertTrue(safe.endsWith("valid_image.png"))

        assertEquals("", repository.mediaFilePath("../secret.txt"))
        assertEquals(safe, repository.mediaFilePath("valid_image.png"))
    }

    @Test
    fun saveMediaFileRejectsPathTraversal() = runTest {
        val result1 = repository.saveMediaFile("../user_data.db")
        assertTrue(result1 is LocalContentRepository.SaveMediaResult.InvalidFileName)

        val result2 = repository.saveMediaFile("..")
        assertTrue(result2 is LocalContentRepository.SaveMediaResult.InvalidFileName)

        val result3 = repository.saveMediaFile("   ")
        assertTrue(result3 is LocalContentRepository.SaveMediaResult.InvalidFileName)
    }

    @Test
    fun resolveOverlayPathsDoesNotSelfMapSvgFiles() = runTest {
        val mediaDir = File(StorageProvider.getMediaDirectory())
        mediaDir.mkdirs()
        val realSvgFile = File(mediaDir, "big___test_overlay_check.svg")
        realSvgFile.writeText("<svg></svg>")

        try {
            val input = listOf("big___test_overlay_check.svg", "normal_photo.png")
            val overlays = repository.resolveOverlayPaths(input)

            assertEquals(2, overlays.size)
            // Even though big___test_overlay_check.svg exists on disk, it must NOT map to itself as an overlay
            assertNull(overlays["big___test_overlay_check.svg"])
            assertNull(overlays["normal_photo.png"])
        } finally {
            realSvgFile.delete()
        }
    }
}
