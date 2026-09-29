package com.medqb.app.shared.data.dao

import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.data.local.entity.LogEntity
import com.medqb.app.shared.data.local.entity.QuizSessionEntity
import com.medqb.app.shared.data.local.entity.SessionLogLinkEntity
import kotlin.time.Clock

/**
 * Writes answer logs and their session links. Both live in the same user database,
 * so each multi-step write is fully atomic.
 */
class LogDao(
    private val userDataManager: UserDataManager,
) {
    suspend fun logAnswer(
        dbName: String,
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        sessionId: String
    ) {
        val dateString = Clock.System.now().toString()

        val logEntity = LogEntity(
            dbName = dbName,
            qid = qid,
            selectedAnswer = selectedAnswer,
            corrAnswer = corrAnswer,
            time = time,
            answerDate = dateString
        )

        val logDao = userDataManager.logDao()
        if (sessionId.isBlank()) {
            logDao.insert(logEntity)
        } else {
            val historyDao = userDataManager.sessionHistoryDao()
            userDataManager.withTransaction {
                val insertedId = logDao.insert(logEntity)
                historyDao.ensureSessionExists(QuizSessionEntity(sessionId))
                historyDao.insertLogLink(SessionLogLinkEntity(sessionId, insertedId))
            }
        }
    }

    suspend fun clearLogForQuestion(dbName: String, qid: Long) {
        userDataManager.logDao().clearForQuestion(dbName, qid)
    }
}
