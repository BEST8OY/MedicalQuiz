package com.medicalquiz.app.shared.platform

import android.os.Environment
import java.io.File

actual object StorageProvider {
    actual fun getAppStorageDirectory(): String {
        val legacyDir = File(Environment.getExternalStorageDirectory(), "MedicalQuiz")
        if (legacyDir.exists()) return legacyDir.absolutePath
        
        // Fallback to scoped storage if legacy dir doesn't exist or isn't accessible
        return AppContext.context.getExternalFilesDir(null)?.absolutePath 
            ?: AppContext.context.filesDir.absolutePath
    }
}
