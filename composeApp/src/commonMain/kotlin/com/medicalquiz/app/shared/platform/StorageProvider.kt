package com.medicalquiz.app.shared.platform

expect object StorageProvider {
    fun getAppStorageDirectory(): String
}
