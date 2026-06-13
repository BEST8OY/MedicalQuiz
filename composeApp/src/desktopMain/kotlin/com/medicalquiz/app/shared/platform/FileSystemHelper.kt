package com.medicalquiz.app.shared.platform

import java.io.File

actual object FileSystemHelper {
    actual fun exists(path: String): Boolean {
        return File(path).exists()
    }

    actual fun getMediaFile(fileName: String): String? {
        val mediaFile = File(StorageProvider.getMediaDirectory(), fileName)
        if (mediaFile.exists()) return mediaFile.absolutePath
        return null
    }

    actual fun readText(path: String): String? {
        val file = File(path)
        return if (file.exists() && file.canRead()) file.readText() else null
    }

    actual fun writeText(path: String, content: String) {
        val file = File(path)
        val tmpFile = File(path + ".tmp")
        file.parentFile?.mkdirs()
        tmpFile.writeText(content)
        if (!tmpFile.renameTo(file)) {
            file.writeText(content)
            tmpFile.delete()
        }
    }

    actual fun copyFile(source: String, destination: String): Boolean {
        return try {
            val srcFile = File(source)
            val dstFile = File(destination)
            dstFile.parentFile?.mkdirs()
            srcFile.copyTo(dstFile, overwrite = true)
            true
        } catch (e: Exception) {
            Logger.e("FileSystemHelper", "Failed to copy file: $source -> $destination", e)
            false
        }
    }

    actual fun delete(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            Logger.e("FileSystemHelper", "Failed to delete: $path", e)
            false
        }
    }

    actual fun getDatabasePath(dbName: String): String {
        return "${StorageProvider.getDatabaseDirectory()}/$dbName"
    }

    actual fun listDatabases(): List<String> {
        val databasesDir = java.io.File(StorageProvider.getDatabaseDirectory())
        if (!databasesDir.exists()) return emptyList()
        return databasesDir.listFiles { file -> file.extension == "db" }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }
}
