package com.medqb.app.shared.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.local.UserDatabase
import com.medqb.app.shared.data.local.UserDatabaseConstructor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserDataManagerTest {

    private fun buildInMemoryDb(): UserDatabase =
        Room.inMemoryDatabaseBuilder<UserDatabase> { UserDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun callerCancellationMidReinitializeStillOpensDatabaseAndClearsInFlightState() = runBlocking {
        val buildCalls = AtomicInteger(0)
        val reinitBuildStarted = CompletableDeferred<Unit>()
        val releaseSecondBuild = CompletableDeferred<Unit>()

        val manager = object : UserDataManager() {
            override fun buildDatabase(): UserDatabase {
                val call = buildCalls.incrementAndGet()
                if (call == 2) {
                    reinitBuildStarted.complete(Unit)
                    assertTrue(runBlocking { withTimeout(5_000) { releaseSecondBuild.await(); true } })
                }
                return buildInMemoryDb()
            }
        }

        try {
            manager.init()
            assertEquals(1, buildCalls.get())

            val reinitJob = launch(Dispatchers.Default) { manager.reinitialize() }
            withTimeout(5_000) { reinitBuildStarted.await() }
            assertTrue(manager.isReinitializingOrClosed())

            // Simulate Activity recreation cancelling the caller while reinitialize is in flight.
            reinitJob.cancel()
            assertTrue(manager.isReinitializingOrClosed())

            val awaiter = async(Dispatchers.Default) { manager.awaitOpen() }
            releaseSecondBuild.complete(Unit)
            withTimeout(5_000) { awaiter.await() }

            assertFalse(manager.isReinitializingOrClosed())
            assertTrue(manager.dbState.value is UserDataManager.DbState.Open)
            assertEquals(2, buildCalls.get())
        } finally {
            releaseSecondBuild.complete(Unit)
            manager.close()
        }
    }

    @Test
    fun concurrentReinitializeCallsSerializeWithoutDroppingActiveTracking() = runBlocking {
        val buildCalls = AtomicInteger(0)
        val firstBuildStarted = CompletableDeferred<Unit>()
        val releaseFirstBuild = CompletableDeferred<Unit>()

        val manager = object : UserDataManager() {
            override fun buildDatabase(): UserDatabase {
                val call = buildCalls.incrementAndGet()
                if (call == 1) {
                    firstBuildStarted.complete(Unit)
                    assertTrue(runBlocking { withTimeout(5_000) { releaseFirstBuild.await(); true } })
                }
                return buildInMemoryDb()
            }
        }

        try {
            val first = async(Dispatchers.Default) { manager.reinitialize() }
            withTimeout(5_000) { firstBuildStarted.await() }

            val second = async(Dispatchers.Default) { manager.reinitialize() }
            releaseFirstBuild.complete(Unit)

            withTimeout(5_000) {
                first.await()
                second.await()
            }

            assertFalse(manager.isReinitializingOrClosed())
            assertTrue(manager.dbState.value is UserDataManager.DbState.Open)
            assertEquals(2, buildCalls.get())
        } finally {
            releaseFirstBuild.complete(Unit)
            manager.close()
        }
    }

    @Test
    fun cancellingCallerWhileReinitializeWaitsDoesNotPreventCompletion() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val buildCalls = AtomicInteger(0)

        val manager = object : UserDataManager() {
            override fun buildDatabase(): UserDatabase {
                buildCalls.incrementAndGet()
                started.complete(Unit)
                assertTrue(runBlocking { withTimeout(5_000) { release.await(); true } })
                return buildInMemoryDb()
            }
        }

        try {
            val job = launch(Dispatchers.Default) {
                manager.reinitialize()
                gate.complete(Unit)
            }
            withTimeout(5_000) { started.await() }
            job.cancelAndJoin()
            assertFalse(gate.isCompleted)

            release.complete(Unit)
            withTimeout(5_000) { manager.awaitOpen() }

            assertFalse(manager.isReinitializingOrClosed())
            assertTrue(manager.dbState.value is UserDataManager.DbState.Open)
            assertEquals(1, buildCalls.get())
        } finally {
            release.complete(Unit)
            manager.close()
        }
    }
}
