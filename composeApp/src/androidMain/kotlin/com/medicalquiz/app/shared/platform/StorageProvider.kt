package com.medicalquiz.app.shared.platform

import android.os.Build
import android.os.Environment
import java.io.File

actual object StorageProvider {
    private var resolvedDirectory: String? = null

    actual fun getAppStorageDirectory(): String {
        resolvedDirectory?.let { return it }

        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            File(Environment.getExternalStorageDirectory(), "MedicalQuiz")
        } else {
            AppContext.context.getExternalFilesDir(null)
                ?: AppContext.context.filesDir
        }

        return dir.absolutePath.also { resolvedDirectory = it }
    }

    actual fun getMediaDirectory(): String = "${getAppStorageDirectory()}/media"

    actual fun getDatabaseDirectory(): String = "${getAppStorageDirectory()}/QBanks"

    actual fun hasDatabaseFolder(): Boolean {
        val dbDir = File(getDatabaseDirectory())
        return dbDir.exists() && dbDir.listFiles()?.any { it.extension == "db" } == true
    }
}
