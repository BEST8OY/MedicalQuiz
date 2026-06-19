package com.medqb.app.shared.platform

import java.io.File

actual object StorageProvider {
    actual fun getAppStorageDirectory(): String {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".medqb")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return appDir.absolutePath
    }

    actual fun getMediaDirectory(): String = "${getAppStorageDirectory()}/media"

    actual fun getDatabaseDirectory(): String = "${getAppStorageDirectory()}/QBanks"

    actual fun hasDatabaseFolder(): Boolean {
        val dbDir = File(getDatabaseDirectory())
        return dbDir.exists() && dbDir.listFiles()?.any { it.extension == "db" } == true
    }
}
