package com.medicalquiz.app.data.database

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages SQLite database connection lifecycle
 */
class DatabaseConnection(private val dbPath: String) {
    private var database: SQLiteDatabase? = null
    
    /**
     * Open the database connection
     */
    suspend fun open() = withContext(Dispatchers.IO) {
        if (database?.isOpen == true) return@withContext
        
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            throw IllegalStateException("Database file not found: $dbPath")
        }
        
        database = SQLiteDatabase.openDatabase(
            dbPath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).also { openedDb ->
            // WAL improves concurrent reads/writes and reduces fsync overhead
            openedDb.enableWriteAheadLogging()
        }
    }
    
    /**
     * Close the database connection
     */
    fun close() {
        database?.close()
        database = null
    }
    
    /**
     * Get the database instance, throwing if not opened
     */
    fun getDatabase(): SQLiteDatabase {
        return database ?: throw IllegalStateException("Database not opened. Call open() first.")
    }
    
    /**
     * Check if database is open
     */
    fun isOpen(): Boolean = database?.isOpen == true
}
