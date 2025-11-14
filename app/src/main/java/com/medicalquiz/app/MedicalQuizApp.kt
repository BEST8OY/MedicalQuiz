package com.medicalquiz.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class MedicalQuizApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    companion object {
        private var currentDatabaseManager: com.medicalquiz.app.data.database.DatabaseManager? = null
        
        suspend fun switchDatabase(newDbPath: String): com.medicalquiz.app.data.database.DatabaseManager {
            // Flush and close old database if exists
            currentDatabaseManager?.closeDatabase()
            
            // Create and open new database
            val newManager = com.medicalquiz.app.data.database.DatabaseManager(newDbPath)
            newManager.openDatabase()
            currentDatabaseManager = newManager
            
            return newManager
        }
        
        suspend fun closeCurrentDatabase() {
            currentDatabaseManager?.closeDatabase()
            currentDatabaseManager = null
        }
    }
}
