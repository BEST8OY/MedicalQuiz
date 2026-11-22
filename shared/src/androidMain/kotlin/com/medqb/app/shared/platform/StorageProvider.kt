package com.medqb.app.shared.platform

import android.os.Build
import android.os.Environment
import java.io.File

actual object StorageProvider {
    actual fun getInternalDataDirectory(): String =
        AppContext.context.filesDir.absolutePath

    actual fun getContentDirectory(): String {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            File(Environment.getExternalStorageDirectory(), "MedQB")
        } else {
            AppContext.context.getExternalFilesDir(null)
                ?: AppContext.context.filesDir
        }
        return dir.absolutePath
    }

    actual fun getAppStorageDirectory(): String = getContentDirectory()

    actual fun getMediaDirectory(): String = "${getContentDirectory()}/media"

    actual fun getDatabaseDirectory(): String = "${getContentDirectory()}/QBanks"

    actual fun getSavedMediaDirectory(): String = "${getContentDirectory()}/saved_media"

    actual fun getImageCacheDirectory(): String =
        File(AppContext.context.cacheDir, "image_cache").absolutePath

    actual fun hasDatabaseFolder(): Boolean {
        val dbDir = File(getDatabaseDirectory())
        return dbDir.exists() && dbDir.listFiles()?.any { it.isFile && it.extension.equals("db", ignoreCase = true) } == true
    }

    actual fun ensureDirectoryExists(path: String) {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val mediaDir = when {
            dir.name.equals("media", ignoreCase = true) -> dir
            dir.name.equals("MedQB", ignoreCase = true) -> File(dir, "media").apply { if (!exists()) mkdirs() }
            else -> null
        }
        if (mediaDir != null && mediaDir.exists()) {
            val nomedia = File(mediaDir, ".nomedia")
            if (!nomedia.exists()) {
                try {
                    nomedia.createNewFile()
                } catch (_: Exception) {
                    // Ignore failure if unable to create .nomedia
                }
            }
        }
    }
}
