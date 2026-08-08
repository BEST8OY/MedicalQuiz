package com.medqb.app.shared.data.dao

import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.local.dao.RoomSessionHistoryDao
import com.medqb.app.shared.data.local.entity.LogEntity
import com.medqb.app.shared.data.local.entity.QuizSessionEntity
import com.medqb.app.shared.data.local.entity.SessionLogLinkEntity
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class LogDao(
    private val logDao: RoomLogDao,
    private val sessionHistoryDao: RoomSessionHistoryDao,
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
        val insertedId = logDao.insert(logEntity)

        if (sessionId.isNotBlank()) {
            sessionHistoryDao.ensureSessionExists(QuizSessionEntity(sessionId))
            sessionHistoryDao.insertLogLink(
                SessionLogLinkEntity(sessionId, insertedId)
            )
        }
    }

    suspend fun clearLogForQuestion(dbName: String, qid: Long) {
        val rowids = logDao.getLogRowIds(dbName, qid)
        logDao.clearForQuestion(dbName, qid)
        if (rowids.isNotEmpty()) {
            sessionHistoryDao.cleanupLinksForLogs(rowids)
        }
    }
}
