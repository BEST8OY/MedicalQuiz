package com.medicalquiz.app.shared.platform

import android.os.Build
import android.os.Environment
import java.io.File

actual object StorageProvider {
    private var resolvedDirectory: String? = null

    actual fun getAppStorageDirectory(): String {
        resolvedDirectory?.let { return it }

        val scopedDir = AppContext.context.getExternalFilesDir(null)

        // Check for legacy data migration path (pre-scoped-storage)
        @Suppress("DEPRECATION")
        val legacyDir = File(Environment.getExternalStorageDirectory(), "MedicalQuiz")

        // Use legacy path if it exists (backward compatibility), otherwise scoped storage
        val dir = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && legacyDir.exists() && legacyDir.canRead()) {
            legacyDir
        } else {
            scopedDir ?: AppContext.context.filesDir
        }

        return dir.absolutePath.also { resolvedDirectory = it }
    }
}
