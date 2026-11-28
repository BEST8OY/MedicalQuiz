package com.medqb.app.shared.data

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.local.dao.RoomMarkedQuestionDao
import com.medqb.app.shared.data.local.dao.RoomSessionHistoryDao
import com.medqb.app.shared.data.local.dao.RoomTextHighlightDao
import com.medqb.app.shared.di.AppScope
import com.medqb.app.shared.platform.Logger
import com.medqb.app.shared.platform.StorageProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * Owns the single user database (logs, text highlights, quiz sessions/history) via Room.
 *
 * All multi-statement writes that span DAOs (e.g. log insert + session link) must go
 * through [withTransaction] so they commit atomically — there is only one database now.
 */
@Inject
@SingleIn(AppScope::class)
open class UserDataManager {
    sealed interface DbState {
        data object Closed : DbState
        data class Open(val epoch: Long) : DbState
    }

    // Process-scoped so permission-grant reinitialization survives Activity config-change recreation.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val reinitGuard = Mutex()

    @Volatile
    private var database: UserDatabase? = null

    @Volatile
    private var activeReinit: Deferred<Unit>? = null

    private var currentEpoch = 0L
    private var wasClosed = false
    private var lastOpenFailed = false

    private val _dbState = MutableStateFlow<DbState>(DbState.Open(0L))
    open val dbState: StateFlow<DbState> = _dbState.asStateFlow()

    protected fun setDbState(state: DbState) {
        _dbState.value = state
    }

    protected fun nextOpenEpoch(): Long = ++currentEpoch

    open fun isReinitializingOrClosed(): Boolean =
        activeReinit?.isActive == true || _dbState.value is DbState.Closed

    private val dbDir: String
        get() = StorageProvider.getContentDirectory()

    private val dbPath: String
        get() = "$dbDir/user_data.db"

    internal open fun buildDatabase(): UserDatabase {
        StorageProvider.ensureDirectoryExists(dbDir)
        return Room.databaseBuilder<UserDatabase>(dbPath)
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
    }

    private suspend fun getDatabase(): UserDatabase {
        database?.let { return it }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                database?.let { return@withLock it }
                openDatabaseLocked()
            }
        }
    }

    private fun openDatabaseLocked(): UserDatabase {
        try {
            val db = buildDatabase()
            database = db
            if (wasClosed || lastOpenFailed) {
                wasClosed = false
                lastOpenFailed = false
                _dbState.value = DbState.Open(nextOpenEpoch())
            }
            return db
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastOpenFailed = true
            Logger.e("UserDataManager", "Error initializing user data database", e)
            throw e
        }
    }

    open suspend fun init(): Unit {
        withContext(Dispatchers.IO) {
            getDatabase()
        }
    }

    /**
     * Closes any previously open user database and opens a fresh instance on the app scope so
     * caller (Activity) cancellation during a config change cannot leave the singleton closed.
     */
    open suspend fun reinitialize() {
        val job = reinitGuard.withLock {
            val previous = activeReinit
            _dbState.value = DbState.Closed
            lateinit var created: Deferred<Unit>
            created = scope.async(start = CoroutineStart.LAZY) {
                try {
                    if (previous != null) {
                        runCatching { previous.await() }
                    }
                    mutex.withLock {
                        database?.close()
                        database = null
                        wasClosed = true
                        _dbState.value = DbState.Closed
                        openDatabaseLocked()
                        Unit
                    }
                } finally {
                    withContext(NonCancellable) {
                        reinitGuard.withLock {
                            if (activeReinit === created) {
                                activeReinit = null
                            }
                        }
                    }
                }
            }
            activeReinit = created
            created.start()
            created
        }
        job.await()
    }

    /**
     * Awaits any in-flight [reinitialize] on the app scope and ensures the database is open.
     */
    open suspend fun awaitOpen() {
        val inFlight = reinitGuard.withLock { activeReinit }
        if (inFlight != null) {
            inFlight.await()
        } else if (database == null || _dbState.value is DbState.Closed) {
            init()
        }
    }

    open suspend fun logDao(): RoomLogDao = getDatabase().logDao()

    open suspend fun sessionHistoryDao(): RoomSessionHistoryDao = getDatabase().sessionHistoryDao()

    open suspend fun textHighlightDao(): RoomTextHighlightDao = getDatabase().textHighlightDao()

    open suspend fun markedQuestionDao(): RoomMarkedQuestionDao = getDatabase().markedQuestionDao()

    /**
     * Runs [block] inside a single write transaction on the user database.
     */
    open suspend fun <R> withTransaction(block: suspend () -> R): R =
        withContext(Dispatchers.IO) {
            getDatabase().withWriteTransaction { block() }
        }

    open suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            database?.close()
            database = null
            wasClosed = true
            _dbState.value = DbState.Closed
        }
    }
}
