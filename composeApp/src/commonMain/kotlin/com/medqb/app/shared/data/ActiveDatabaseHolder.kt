package com.medqb.app.shared.data

import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe holder for the active database provider connection and active database name.
 */
@Inject
@SingleIn(AppScope::class)
class ActiveDatabaseHolder {
    private val mutex = Mutex()
    
    private val _databaseProvider = MutableStateFlow<DatabaseProvider?>(null)
    val databaseProvider: StateFlow<DatabaseProvider?> = _databaseProvider.asStateFlow()
    
    private val _databaseName = MutableStateFlow<String>("")
    val databaseName: StateFlow<String> = _databaseName.asStateFlow()

    suspend fun setDatabase(name: String, provider: DatabaseProvider) = mutex.withLock {
        val oldDb = _databaseProvider.value
        _databaseProvider.value = provider
        _databaseName.value = name.removeSuffix(".db")

        if (oldDb != null && oldDb !== provider) {
            try {
                withContext(Dispatchers.IO) {
                    oldDb.closeDatabase()
                }
            } catch (e: Exception) {
                Logger.e("ActiveDatabaseHolder", "Error closing old database connection", e)
            }
        }
    }

    suspend fun closeDatabase() = mutex.withLock {
        val oldDb = _databaseProvider.value
        _databaseProvider.value = null
        _databaseName.value = ""
        if (oldDb != null) {
            try {
                withContext(Dispatchers.IO) {
                    oldDb.closeDatabase()
                }
            } catch (e: Exception) {
                Logger.e("ActiveDatabaseHolder", "Error closing database connection", e)
            }
        }
    }
}
