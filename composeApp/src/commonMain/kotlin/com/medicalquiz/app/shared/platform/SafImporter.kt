package com.medicalquiz.app.shared.platform

expect object SafImporter {
    suspend fun importDatabases(): List<String>
}
