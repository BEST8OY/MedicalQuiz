package com.medicalquiz.app.shared.platform

expect object FileSystemHelper {
    fun exists(path: String): Boolean
    fun getMediaFile(fileName: String): String?
    fun readText(path: String): String?
    fun writeText(path: String, content: String)
    fun copyFile(source: String, destination: String): Boolean
    fun delete(path: String): Boolean
    fun getDatabasePath(dbName: String): String
    fun listDatabases(): List<String>
}
