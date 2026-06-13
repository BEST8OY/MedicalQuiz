package com.medicalquiz.app.shared.platform

import java.io.File

actual object MediaResolver {
    private var mediaDir: File? = null

    actual fun init() {
        mediaDir = File(StorageProvider.getMediaDirectory())
    }

    actual fun hasMediaFile(fileName: String): Boolean {
        val dir = mediaDir ?: return false
        return File(dir, fileName).exists()
    }

    actual fun getMediaUri(fileName: String): String? {
        val dir = mediaDir ?: return null
        val file = File(dir, fileName)
        return if (file.exists()) file.absolutePath else null
    }

    actual fun readMediaText(fileName: String): String? {
        val dir = mediaDir ?: return null
        val file = File(dir, fileName)
        return if (file.exists() && file.canRead()) file.readText() else null
    }

    actual fun readMediaBytes(fileName: String): ByteArray? {
        val dir = mediaDir ?: return null
        val file = File(dir, fileName)
        return if (file.exists() && file.canRead()) file.readBytes() else null
    }

    actual fun listMediaFiles(): List<String> {
        val dir = mediaDir ?: return emptyList()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()
    }
}
