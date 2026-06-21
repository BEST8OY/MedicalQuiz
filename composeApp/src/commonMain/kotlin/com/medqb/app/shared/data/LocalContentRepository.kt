package com.medqb.app.shared.data

import com.medqb.app.shared.platform.FileSystemHelper
import com.medqb.app.shared.platform.StorageProvider
import com.medqb.app.shared.utils.HtmlUtils
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Inject
class LocalContentRepository {

    suspend fun listDatabases(): List<String> = withContext(Dispatchers.IO) {
        FileSystemHelper.listDatabases()
    }

    suspend fun loadHtmlDocument(fileName: String): HtmlDocumentResult = withContext(Dispatchers.IO) {
        val path = mediaFilePath(fileName)
        val exists = FileSystemHelper.exists(path)
        if (!exists) {
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

    fun mediaFilePath(fileName: String): String =
        "${StorageProvider.getMediaDirectory()}/$fileName"

    suspend fun mediaFileExists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        FileSystemHelper.exists(mediaFilePath(fileName))
    }

    suspend fun resolveOverlayPaths(mediaFiles: List<String>): Map<String, String?> = withContext(Dispatchers.IO) {
        mediaFiles.associateWith { fileName ->
            if (!fileName.startsWith("big_", ignoreCase = true)) return@associateWith null
            val overlayFile = fileName.substringBeforeLast('.') + ".svg"
            val overlayPath = mediaFilePath(overlayFile)
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
        val saveDir = StorageProvider.getAppStorageDirectory() + "/saved_media"
        val sanitizedName = fileName.substringAfterLast("/").substringAfterLast("\\")
        val destPath = "$saveDir/$sanitizedName"
        if (!java.io.File(destPath).canonicalPath.startsWith(java.io.File(saveDir).canonicalPath)) {
            return@withContext SaveMediaResult.InvalidFileName
        }
        val sourcePath = mediaFilePath(fileName)
        val success = FileSystemHelper.copyFile(sourcePath, destPath)
        if (success) SaveMediaResult.Success(destPath) else SaveMediaResult.CopyFailed
    }
}
