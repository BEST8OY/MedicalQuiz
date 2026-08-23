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
 * Identity of the active question bank: canonical name and open connection as ONE
 * immutable value.
 *
 * Publishing them together means consumers can never observe a torn pair (new
 * provider still labeled with the previous name, or vice versa) across a database
 * switch, and staleness checks reduce to comparing one snapshot.
 */
data class ActiveDatabase(
    val name: String,
    val provider: DatabaseProvider,
) {
    /** True when [candidateFileName] (e.g. "bank.db") refers to this database. */
    fun matchesFileName(candidateFileName: String): Boolean =
        name == candidateFileName.removeSuffix(".db")
}

/**
 * Thread-safe holder for the active database connection and its identity.
 */
@Inject
@SingleIn(AppScope::class)
class ActiveDatabaseHolder {
    private val mutex = Mutex()

    private val _activeDatabase = MutableStateFlow<ActiveDatabase?>(null)
    val activeDatabase: StateFlow<ActiveDatabase?> = _activeDatabase.asStateFlow()

    suspend fun setDatabase(name: String, provider: DatabaseProvider) = mutex.withLock {
        val old = _activeDatabase.value
        _activeDatabase.value = ActiveDatabase(name.removeSuffix(".db"), provider)
        closeQuietly(old?.provider, unlessSameAs = provider)
    }

    suspend fun closeDatabase() = mutex.withLock {
        val old = _activeDatabase.value
        _activeDatabase.value = null
        closeQuietly(old?.provider)
    }

    private suspend fun closeQuietly(
        provider: DatabaseProvider?,
        unlessSameAs: DatabaseProvider? = null,
    ) {
        if (provider == null || provider === unlessSameAs) return
        try {
            withContext(Dispatchers.IO) {
                provider.closeDatabase()
            }
        } catch (e: Exception) {
            Logger.e("ActiveDatabaseHolder", "Error closing old database connection", e)
        }
    }
}
