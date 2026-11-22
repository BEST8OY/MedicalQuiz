package com.medqb.app.shared.platform

expect object StorageProvider {
    /**
     * Directory for app-private persistent data (e.g. preferences DataStore).
     * On Android: context.filesDir.
     * On Desktop: ~/.medqb.
     */
    fun getInternalDataDirectory(): String

    /**
     * Directory for user-accessible external content (QBanks, media, saved_media, user_data.db).
     * On Android: /storage/emulated/0/MedQB (if storage manager permission granted) or externalFilesDir.
     * On Desktop: ~/.medqb.
     */
    fun getContentDirectory(): String

    fun getAppStorageDirectory(): String

    fun getMediaDirectory(): String

    fun getDatabaseDirectory(): String

    fun getSavedMediaDirectory(): String

    fun getImageCacheDirectory(): String

    fun hasDatabaseFolder(): Boolean

    /**
     * Ensures that the specified directory exists, creating it and any parent
     * directories if necessary. Safe to call from I/O threads at initialization/write sites.
     */
    fun ensureDirectoryExists(path: String)
}
