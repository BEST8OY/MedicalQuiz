package com.medqb.app.shared.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.dao.LogDao
import com.medqb.app.shared.data.dao.QuestionDao
import com.medqb.app.shared.data.dao.SubjectDao
import com.medqb.app.shared.data.database.DatabaseProvider
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.database.QuestionDetails
import com.medqb.app.shared.data.database.QuestionPerformance
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
    private val userDataManager: UserDataManager,
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
        subjectDao = SubjectDao(connProvider, mutex)
        questionDao = QuestionDao(
            connProvider,
            mutex,
            { isStringIds },
            getLogDao = { userDataManager.logDao() }
        )
        logDao = LogDao(userDataManager)
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
    ): QuestionDetails {
        val (question, answers) = questionDao.getQuestionWithDetails(questionId)
        val performance = if (loadPerformance && question != null) {
            getQuestionPerformance(questionId)
        } else null
        return QuestionDetails.from(question, answers, performance)
    }

    override suspend fun countQuestionIds(
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter,
    ): Int = questionDao.countQuestionIds(dbName, subjectIds, systemIds, performanceFilter)

    override suspend fun getSubjects(): List<Subject> = subjectDao.getSubjects()

    override suspend fun getSystems(subjectIds: List<Long>?): List<System> = subjectDao.getSystems(subjectIds)

    override suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        sessionId: String
    ) = logDao.logAnswer(dbName, qid, selectedAnswer, corrAnswer, time, sessionId)

    override suspend fun clearLogForQuestion(qid: Long) = logDao.clearLogForQuestion(dbName, qid)

    override suspend fun getQuestionPerformance(qid: Long): QuestionPerformance? = withContext(Dispatchers.IO) {
        val result = userDataManager.logDao().getQuestionPerformance(dbName, qid) ?: return@withContext null
        QuestionPerformance(
            qid = qid,
            lastCorrect = result.lastCorrect == 1L,
            everCorrect = result.everCorrect == 1L,
            everIncorrect = result.everIncorrect == 1L,
            attempts = result.attempts.toInt(),
            correctCount = result.correctCount.toInt(),
            incorrectCount = result.incorrectCount.toInt(),
        )
    }
}
