package com.medqb.app.shared.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.dao.LogDao
import com.medqb.app.shared.data.dao.QuestionDao
import com.medqb.app.shared.data.dao.SubjectDao
import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.local.dao.RoomSessionHistoryDao
import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.Question
import com.medqb.app.shared.data.models.Subject
import com.medqb.app.shared.data.models.System
import com.medqb.app.shared.platform.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DatabaseManager(
    private val dbPath: String,
    val dbName: String,
    private val sessionHistoryDao: RoomSessionHistoryDao,
    private val roomLogDao: RoomLogDao,
) : DatabaseProvider {
    private val driver = BundledSQLiteDriver()
    private var connection: SQLiteConnection? = null
    private val mutex = Mutex()
    private var isStringIds: Boolean = true

    val questionDao: QuestionDao
    val subjectDao: SubjectDao
    val logDao: LogDao

    init {
        val connProvider = { getConnection() }
        questionDao = QuestionDao(connProvider, mutex, { isStringIds }, ::getSubjectNames, ::getSystemNames, roomLogDao)
        subjectDao = SubjectDao(connProvider, mutex)
        logDao = LogDao(roomLogDao, sessionHistoryDao)
    }

    suspend fun init() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                connection = driver.open(dbPath)
                getConnection().prepare("PRAGMA foreign_keys = ON").use { stmt ->
                    stmt.step()
                }
                checkSchema()
            } catch (e: Exception) {
                Logger.e("DatabaseManager", "Error initializing database", e)
                throw e
            }
        }
    }

    private fun checkSchema() {
        val conn = connection ?: throw IllegalStateException("Database not initialized")
        try {
            conn.prepare("SELECT type FROM pragma_table_info('Questions') WHERE name = 'subId'").use { stmt ->
                if (stmt.step()) {
                    val type = stmt.getText(0)
                    isStringIds = type.contains("char", ignoreCase = true) ||
                                  type.contains("text", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            Logger.e("DatabaseManager", "Schema check failed, defaulting to string IDs", e)
            isStringIds = true
        }
    }

    private fun getConnection(): SQLiteConnection {
        return connection ?: throw IllegalStateException("Database not initialized")
    }

    override suspend fun closeDatabase() = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection?.close()
            connection = null
        }
    }

    override suspend fun getQuestionIds(
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter
    ): List<Long> = questionDao.getQuestionIds(dbName, subjectIds, systemIds, performanceFilter)

    override suspend fun getQuestionById(id: Long): Question? = questionDao.getQuestionById(id)

    override suspend fun getAnswersForQuestion(questionId: Long): List<Answer> = questionDao.getAnswersForQuestion(questionId)

    override suspend fun getQuestionWithDetails(
        questionId: Long,
        loadPerformance: Boolean,
    ): Triple<Question?, List<Answer>, QuestionPerformance?> {
        val (question, answers, _) = questionDao.getQuestionWithDetails(questionId)
        val performance = if (loadPerformance && question != null) {
            getQuestionPerformance(dbName, questionId)
        } else null
        return Triple(question, answers, performance)
    }

    override suspend fun countQuestionIds(
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter,
    ): Int = questionDao.countQuestionIds(dbName, subjectIds, systemIds, performanceFilter)

    override suspend fun getSubjects(): List<Subject> = subjectDao.getSubjects()

    override suspend fun getSystems(subjectIds: List<Long>?): List<System> = subjectDao.getSystems(subjectIds)

    override suspend fun logAnswer(
        dbName: String,
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        sessionId: String
    ) = logDao.logAnswer(dbName, qid, selectedAnswer, corrAnswer, time, sessionId)

    override suspend fun clearLogForQuestion(dbName: String, qid: Long) = logDao.clearLogForQuestion(dbName, qid)

    override suspend fun getQuestionPerformance(dbName: String, qid: Long): QuestionPerformance? = withContext(Dispatchers.IO) {
        roomLogDao.getQuestionPerformance(dbName, qid)?.let {
            QuestionPerformance(
                qid = qid,
                lastCorrect = it.lastCorrect == 1L,
                everCorrect = it.everCorrect == 1L,
                everIncorrect = it.everIncorrect == 1L,
                attempts = it.attempts.toInt(),
                correctCount = it.correctCount.toInt(),
                incorrectCount = it.incorrectCount.toInt(),
            )
        }
    }

    private fun getSubjectNames(idsStr: String): String = subjectDao.getSubjectNames(idsStr)

    private fun getSystemNames(idsStr: String): String = subjectDao.getSystemNames(idsStr)
}
