package com.medicalquiz.app.shared.platform

expect object MediaResolver {
    fun init()
    fun hasMediaFile(fileName: String): Boolean
    fun getMediaUri(fileName: String): String?
    fun readMediaText(fileName: String): String?
    fun readMediaBytes(fileName: String): ByteArray?
    fun listMediaFiles(): List<String>
}
