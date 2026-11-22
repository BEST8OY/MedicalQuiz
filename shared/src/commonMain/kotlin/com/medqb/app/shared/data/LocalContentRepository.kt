package com.medqb.app.shared.data

import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.FileSystemHelper
import com.medqb.app.shared.platform.StorageProvider
import com.medqb.app.shared.utils.HtmlUtils
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Inject
@SingleIn(AppScope::class)
class LocalContentRepository {

    suspend fun listDatabases(): List<String> = withContext(Dispatchers.IO) {
        FileSystemHelper.listDatabases()
    }

    fun safeMediaFilePath(fileName: String): String? {
        val trimmed = fileName.trim()
        if (trimmed.isBlank() || trimmed == "." || trimmed == "..") return null
        val mediaDir = StorageProvider.getMediaDirectory()
        val path = "$mediaDir/$trimmed"
        return path.takeIf { FileSystemHelper.isSafePath(mediaDir, it) }
    }

    fun mediaFilePath(fileName: String): String =
        safeMediaFilePath(fileName).orEmpty()

    suspend fun loadHtmlDocument(fileName: String): HtmlDocumentResult = withContext(Dispatchers.IO) {
        val path = safeMediaFilePath(fileName)
        if (path == null || !FileSystemHelper.exists(path)) {
            return@withContext HtmlDocumentResult(
                fileExists = false,
                sanitizedHtml = null,
            )
        }

        val raw = FileSystemHelper.readText(path)
        HtmlDocumentResult(
            fileExists = true,
            sanitizedHtml = raw?.let(HtmlUtils::sanitizeForRichText),
        )
    }

    suspend fun mediaFileExists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val path = safeMediaFilePath(fileName) ?: return@withContext false
        FileSystemHelper.exists(path)
    }

    suspend fun resolveOverlayPaths(mediaFiles: List<String>): Map<String, String?> = withContext(Dispatchers.IO) {
        mediaFiles.associateWith { fileName ->
            if (!fileName.startsWith("big_", ignoreCase = true) || fileName.endsWith(".svg", ignoreCase = true)) {
                return@associateWith null
            }
            val overlayFile = fileName.substringBeforeLast('.') + ".svg"
            val overlayPath = safeMediaFilePath(overlayFile) ?: return@associateWith null
            if (FileSystemHelper.exists(overlayPath)) overlayPath else null
        }
    }

    data class HtmlDocumentResult(
        val fileExists: Boolean,
        val sanitizedHtml: String?,
    )

    sealed class SaveMediaResult {
        data class Success(val destPath: String) : SaveMediaResult()
        data object InvalidFileName : SaveMediaResult()
        data object CopyFailed : SaveMediaResult()
    }

    suspend fun saveMediaFile(fileName: String): SaveMediaResult = withContext(Dispatchers.IO) {
        val sourcePath = safeMediaFilePath(fileName) ?: return@withContext SaveMediaResult.InvalidFileName
        val saveDir = StorageProvider.getSavedMediaDirectory()
        val sanitizedName = fileName.substringAfterLast("/").substringAfterLast("\\").trim()
        if (sanitizedName.isBlank() || sanitizedName == "." || sanitizedName == "..") {
            return@withContext SaveMediaResult.InvalidFileName
        }
        val destPath = "$saveDir/$sanitizedName"
        if (!FileSystemHelper.isSafePath(saveDir, destPath)) {
            return@withContext SaveMediaResult.InvalidFileName
        }
        val success = FileSystemHelper.copyFile(sourcePath, destPath)
        if (success) SaveMediaResult.Success(destPath) else SaveMediaResult.CopyFailed
    }
}
