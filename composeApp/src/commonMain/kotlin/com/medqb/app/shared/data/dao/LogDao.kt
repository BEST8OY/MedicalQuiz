package com.medqb.app.shared.data.dao

import com.medqb.app.shared.data.UserDataManager
import com.medqb.app.shared.data.local.entity.LogEntity
import com.medqb.app.shared.data.local.entity.QuizSessionEntity
import com.medqb.app.shared.data.local.entity.SessionLogLinkEntity
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Writes answer logs and their session links. Both live in the same user database,
 * so each multi-step write is fully atomic.
 */
class LogDao(
    private val userDataManager: UserDataManager,
) {
    @OptIn(ExperimentalTime::class)
    suspend fun logAnswer(
        dbName: String,
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        sessionId: String
    ) {
        val now = Clock.System.now()
        val dateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val monthNum = dateTime.month.ordinal + 1
        val dateString = "${dateTime.year}-${monthNum.toString().padStart(2, '0')}-${dateTime.day.toString().padStart(2, '0')} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}:${dateTime.second.toString().padStart(2, '0')}"

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
        val logDao = userDataManager.logDao()
        val rowids = logDao.getLogRowIds(dbName, qid)
        val historyDao = userDataManager.sessionHistoryDao()
        userDataManager.withTransaction {
            logDao.clearForQuestion(dbName, qid)
            if (rowids.isNotEmpty()) {
                historyDao.cleanupLinksForLogs(rowids)
            }
        }
    }
}
