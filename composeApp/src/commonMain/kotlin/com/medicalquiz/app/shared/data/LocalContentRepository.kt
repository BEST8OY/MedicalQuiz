package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.MediaResolver
import com.medicalquiz.app.shared.utils.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalContentRepository {

    suspend fun listDatabases(): List<String> = withContext(Dispatchers.IO) {
        FileSystemHelper.listDatabases()
    }

    suspend fun loadHtmlDocument(fileName: String): HtmlDocumentResult = withContext(Dispatchers.IO) {
        val exists = MediaResolver.hasMediaFile(fileName)
        if (!exists) {
            return@withContext HtmlDocumentResult(
                fileExists = false,
                sanitizedHtml = null,
            )
        }

        val raw = MediaResolver.readMediaText(fileName)
        HtmlDocumentResult(
            fileExists = true,
            sanitizedHtml = raw?.let(HtmlUtils::sanitizeForRichText),
        )
    }

    fun mediaFilePath(fileName: String): String = MediaResolver.getMediaUri(fileName) ?: ""

    suspend fun mediaFileExists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        MediaResolver.hasMediaFile(fileName)
    }

    suspend fun resolveOverlayPaths(mediaFiles: List<String>): Map<String, String?> = withContext(Dispatchers.IO) {
        mediaFiles.associateWith { fileName ->
            if (!fileName.startsWith("big_", ignoreCase = true)) return@associateWith null
            val overlayFile = fileName.substringBeforeLast('.') + ".svg"
            if (MediaResolver.hasMediaFile(overlayFile)) MediaResolver.getMediaUri(overlayFile) else null
        }
    }

    data class HtmlDocumentResult(
        val fileExists: Boolean,
        val sanitizedHtml: String?,
    )
}
