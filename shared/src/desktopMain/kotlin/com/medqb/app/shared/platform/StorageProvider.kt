package com.medqb.app.shared.platform

import java.io.File

actual object StorageProvider {
    private val baseDir: String by lazy {
        File(System.getProperty("user.home"), ".medqb")
            .apply { if (!exists()) mkdirs() }
            .absolutePath
    }

    actual fun getInternalDataDirectory(): String = baseDir

    actual fun getContentDirectory(): String = baseDir

    actual fun getAppStorageDirectory(): String = getContentDirectory()

    actual fun getMediaDirectory(): String = "${getContentDirectory()}/media"

    actual fun getDatabaseDirectory(): String = "${getContentDirectory()}/QBanks"

    actual fun getSavedMediaDirectory(): String = "${getContentDirectory()}/saved_media"

    actual fun getImageCacheDirectory(): String = "${getInternalDataDirectory()}/image_cache"

    actual fun hasDatabaseFolder(): Boolean {
        val dbDir = File(getDatabaseDirectory())
        return dbDir.exists() && dbDir.listFiles()?.any { it.isFile && it.extension.equals("db", ignoreCase = true) } == true
    }

    actual fun ensureDirectoryExists(path: String) {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }
}
