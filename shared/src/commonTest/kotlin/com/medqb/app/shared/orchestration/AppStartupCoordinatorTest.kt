package com.medqb.app.shared.orchestration

import com.medqb.app.shared.data.ActiveDatabaseHolder
import com.medqb.app.shared.data.LocalContentRepository
import com.medqb.app.shared.data.ManagedDatabaseProvider
import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.viewmodel.FakeDatabaseProvider
import com.medqb.app.shared.viewmodel.FakeUserDataManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupCoordinatorTest {

    private class FakeManagedDatabase(
        private val delegate: FakeDatabaseProvider,
        private val onInit: suspend () -> Unit = {},
        private val onClose: () -> Unit = {},
    ) : ManagedDatabaseProvider, DatabaseProvider by delegate {
        var initStarted = false
        var closeCount = 0

        override suspend fun init() {
            initStarted = true
            onInit()
        }

        override suspend fun closeDatabase() {
            closeCount++
            onClose()
        }
    }

    @Test
    fun closeActiveDatabaseAndHandleDatabaseSelectionAreStrictlySerialized() = runTest {
        val holder = ActiveDatabaseHolder()
        val closeStarted = CompletableDeferred<Unit>()
        val closeGate = CompletableDeferred<Unit>()
        val events = Collections.synchronizedList(mutableListOf<String>())

        try {
            val initialDb = FakeManagedDatabase(
                delegate = FakeDatabaseProvider("Cardio.db"),
                onClose = { events += "close:start" },
            )
            val slowCloseDelegate = object : DatabaseProvider by FakeDatabaseProvider("Cardio.db") {
                override suspend fun closeDatabase() {
                    events += "close:await"
                    closeStarted.complete(Unit)
                    closeGate.await()
                    events += "close:done"
                }
            }
            holder.setDatabase("Cardio.db", slowCloseDelegate)

            val nextDb = FakeManagedDatabase(
                delegate = FakeDatabaseProvider("Renal.db"),
                onInit = { events += "open:Renal.db" },
            )
            val coordinator = object : AppStartupCoordinator(
                localContentRepository = LocalContentRepository(),
                activeDatabaseHolder = holder,
                userDataManager = FakeUserDataManager(),
            ) {
                override fun createDatabaseManager(dbPath: String, canonicalDbName: String): ManagedDatabaseProvider {
                    return if (canonicalDbName == "Renal") nextDb else initialDb
                }
            }

            val closeJob = async { coordinator.closeActiveDatabase() }
            closeStarted.await()
            assertEquals(listOf("close:await"), events.toList())

            val openJob = async { coordinator.handleDatabaseSelection("Renal.db") }
            runCurrent()
            // Open must wait until close completes before initializing Renal.db.
            assertEquals(listOf("close:await"), events.toList())

            closeGate.complete(Unit)
            closeJob.await()
            val decision = openJob.await()

            assertEquals("Renal.db", decision?.initializedDatabase)
            assertEquals(listOf("close:await", "close:done", "open:Renal.db"), events.toList())
            assertEquals("Renal", holder.activeDatabase.value?.name)
        } finally {
            closeStarted.complete(Unit)
            closeGate.complete(Unit)
        }
    }

    @Test
    fun cancellingHandleDatabaseSelectionMidInitClosesDatabaseWithoutLeaking() = runTest {
        val holder = ActiveDatabaseHolder()
        val initGate = CompletableDeferred<Unit>()
        lateinit var createdDb: FakeManagedDatabase

        try {
            val coordinator = object : AppStartupCoordinator(
                localContentRepository = LocalContentRepository(),
                activeDatabaseHolder = holder,
                userDataManager = FakeUserDataManager(),
            ) {
                override fun createDatabaseManager(dbPath: String, canonicalDbName: String): ManagedDatabaseProvider {
                    return FakeManagedDatabase(
                        delegate = FakeDatabaseProvider(canonicalDbName),
                        onInit = { initGate.await() },
                    ).also { createdDb = it }
                }
            }

            val openJob = async { coordinator.handleDatabaseSelection("Cardio.db") }
            runCurrent()
            assertTrue(createdDb.initStarted)

            openJob.cancelAndJoin()

            assertEquals(1, createdDb.closeCount, "Aborted DB open must close its SQLite connection under NonCancellable")
            assertNull(holder.activeDatabase.value, "Cancelled DB open must not publish into ActiveDatabaseHolder")
        } finally {
            initGate.complete(Unit)
        }
    }
}
