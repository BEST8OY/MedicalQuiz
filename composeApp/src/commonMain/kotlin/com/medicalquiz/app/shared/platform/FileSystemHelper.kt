package com.medicalquiz.app.shared.platform

expect object FileSystemHelper {
    fun exists(path: String): Boolean
    fun getMediaFile(fileName: String): String?
    fun readText(path: String): String?
    fun getDatabasePath(dbName: String): String
    fun listDatabases(): List<String>
}
