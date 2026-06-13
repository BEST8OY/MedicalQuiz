package com.medicalquiz.app.shared.data

import com.medicalquiz.app.shared.data.database.DatabaseProvider
import com.medicalquiz.app.shared.data.database.LogsProvider
import com.medicalquiz.app.shared.platform.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ActiveDatabaseHolder {
    private val mutex = Mutex()

    private val _databaseProvider = MutableStateFlow<DatabaseProvider?>(null)
    val databaseProvider: StateFlow<DatabaseProvider?> = _databaseProvider.asStateFlow()

    private val _logsProvider = MutableStateFlow<LogsProvider?>(null)
    val logsProvider: StateFlow<LogsProvider?> = _logsProvider.asStateFlow()

    private val _databaseName = MutableStateFlow<String>("")
    val databaseName: StateFlow<String> = _databaseName.asStateFlow()

    suspend fun setDatabase(name: String, provider: DatabaseProvider, logsProvider: LogsProvider? = null) = mutex.withLock {
        val oldDb = _databaseProvider.value
        val oldLogs = _logsProvider.value
        _databaseProvider.value = provider
        _logsProvider.value = logsProvider
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
        if (oldLogs != null && oldLogs !== logsProvider) {
            try {
                withContext(Dispatchers.IO) {
                    oldLogs.close()
                }
            } catch (e: Exception) {
                Logger.e("ActiveDatabaseHolder", "Error closing old logs connection", e)
            }
        }
    }

    suspend fun closeDatabase() = mutex.withLock {
        val oldDb = _databaseProvider.value
        val oldLogs = _logsProvider.value
        _databaseProvider.value = null
        _logsProvider.value = null
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
        if (oldLogs != null) {
            try {
                withContext(Dispatchers.IO) {
                    oldLogs.close()
                }
            } catch (e: Exception) {
                Logger.e("ActiveDatabaseHolder", "Error closing logs connection", e)
            }
        }
    }
}
