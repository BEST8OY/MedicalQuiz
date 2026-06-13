package com.medicalquiz.app.shared.platform

actual object SafImporter {
    actual suspend fun importDatabases(): List<String> {
        return FileSystemHelper.listDatabases()
    }
}
