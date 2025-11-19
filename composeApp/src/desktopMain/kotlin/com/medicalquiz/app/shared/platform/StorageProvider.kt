package com.medicalquiz.app.shared.platform

import java.io.File

actual object StorageProvider {
    actual fun getAppStorageDirectory(): String {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".medicalquiz")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return appDir.absolutePath
    }
}
