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

    actual fun getDatabasePath(dbName: String): String {
        val storageRoot = StorageProvider.getAppStorageDirectory()
        val dbFile = File(File(storageRoot, "databases"), dbName)
        return dbFile.absolutePath
    }

    actual fun listDatabases(): List<String> {
        val storageRoot = StorageProvider.getAppStorageDirectory()
        val databasesDir = File(storageRoot, "databases")
        if (!databasesDir.exists()) return emptyList()
        return databasesDir.listFiles { file -> file.extension == "db" }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }
}
