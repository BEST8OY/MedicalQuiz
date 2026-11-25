package com.medqb.app.shared.data

import com.medqb.app.shared.data.local.entity.MarkedQuestionEntity
import com.medqb.app.shared.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
class DefaultMarkedQuestionsRepository(
    private val userDataManager: UserDataManager,
) : MarkedQuestionsRepository {

    private val mutationMutex = Mutex()

    private fun canonicalize(dbName: String): String =
        canonicalizeDbName(dbName)

    override suspend fun isMarked(dbName: String, questionId: Long): Boolean {
        val canonical = canonicalize(dbName)
        if (canonical.isBlank()) return false
        return userDataManager.markedQuestionDao().isMarked(canonical, questionId)
    }

    override suspend fun getMarkedQuestionIds(dbName: String): Set<Long> {
        val canonical = canonicalize(dbName)
        if (canonical.isBlank()) return emptySet()
        return userDataManager.markedQuestionDao().getMarkedQuestionIds(canonical).toSet()
    }

    override fun observeMarkedQuestionIds(dbName: String): Flow<Set<Long>> {
        val canonical = canonicalize(dbName)
        if (canonical.isBlank()) return flowOf(emptySet())
        return flow {
            emitAll(
                userDataManager.markedQuestionDao()
                    .observeMarkedQuestionIds(canonical)
                    .map { it.toSet() }
            )
        }
    }

    override suspend fun setMarked(dbName: String, questionId: Long, marked: Boolean): Boolean {
        val canonical = canonicalize(dbName)
        if (canonical.isBlank()) return false
        mutationMutex.withLock {
            val dao = userDataManager.markedQuestionDao()
            if (marked) {
                dao.upsert(
                    MarkedQuestionEntity(
                        dbName = canonical,
                        questionId = questionId,
                        markedAt = Clock.System.now().toEpochMilliseconds(),
                    )
                )
            } else {
                dao.delete(canonical, questionId)
            }
            return marked
        }
    }

    override suspend fun toggleMark(dbName: String, questionId: Long): Boolean {
        val canonical = canonicalize(dbName)
        if (canonical.isBlank()) return false
        mutationMutex.withLock {
            val dao = userDataManager.markedQuestionDao()
            val currentlyMarked = dao.isMarked(canonical, questionId)
            val nextMarked = !currentlyMarked
            if (nextMarked) {
                dao.upsert(
                    MarkedQuestionEntity(
                        dbName = canonical,
                        questionId = questionId,
                        markedAt = Clock.System.now().toEpochMilliseconds(),
                    )
                )
            } else {
                dao.delete(canonical, questionId)
            }
            return nextMarked
        }
    }

    override suspend fun clearForDatabase(dbName: String) {
        val canonical = canonicalize(dbName)
        if (canonical.isBlank()) return
        mutationMutex.withLock {
            userDataManager.markedQuestionDao().clearForDatabase(canonical)
        }
    }
}
