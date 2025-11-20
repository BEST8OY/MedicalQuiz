package com.medicalquiz.app.shared.platform

import java.io.File

actual object FileSystemHelper {
    actual fun exists(path: String): Boolean {
        return File(path).exists()
    }

    actual fun getMediaFile(fileName: String): String? {
        val storageRoot = StorageProvider.getAppStorageDirectory()
        val mediaFile = File(File(storageRoot, "media"), fileName)
        if (mediaFile.exists()) return mediaFile.absolutePath
        return null
    }

    actual fun readText(path: String): String? {
        val file = File(path)
        return if (file.exists() && file.canRead()) file.readText() else null
    }

    actual fun getDatabasePath(dbName: String): String {
        val storageRoot = StorageProvider.getAppStorageDirectory()
        return File(storageRoot, dbName).absolutePath
    }

    actual fun listDatabases(): List<String> {
        val storageRoot = StorageProvider.getAppStorageDirectory()
        val dir = File(storageRoot)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file -> file.extension == "db" }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }
}
