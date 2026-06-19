package com.medqb.app.shared.platform

expect object StorageProvider {
    fun getAppStorageDirectory(): String
    fun getMediaDirectory(): String
    fun getDatabaseDirectory(): String
    fun hasDatabaseFolder(): Boolean
}
