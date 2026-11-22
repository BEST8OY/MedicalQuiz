package com.medqb.app.shared.data.dao

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.database.QuestionStatus
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.database.StatusScope
import com.medqb.app.shared.data.local.dao.QuestionPerformanceResult
import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.local.dao.RoomMarkedQuestionDao
import com.medqb.app.shared.data.local.entity.LogEntity
import com.medqb.app.shared.data.local.entity.MarkedQuestionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QuestionDaoMarkedFilterTest {

    private lateinit var connection: SQLiteConnection
    private val mutex = Mutex()
    private val driver = BundledSQLiteDriver()

    private var stubMarkedQids: List<Long> = emptyList()
    private var stubAllLoggedQids: List<Long> = emptyList()
    private var stubLastIncorrectQids: List<Long> = emptyList()
    private var stubEverIncorrectQids: List<Long> = emptyList()
    private var stubLastCorrectQids: List<Long> = emptyList()
    private var stubEverCorrectQids: List<Long> = emptyList()

    private val stubLogDao = object : RoomLogDao {
        override suspend fun insert(log: LogEntity): Long = 1L
        override suspend fun clearForQuestion(dbName: String, qid: Long) = Unit
        override suspend fun getLogRowIds(dbName: String, qid: Long): List<Long> = emptyList()
        override suspend fun getQuestionPerformance(dbName: String, qid: Long): QuestionPerformanceResult? = null
        override suspend fun getAllLoggedQids(dbName: String): List<Long> = stubAllLoggedQids
        override suspend fun getLastCorrectQids(dbName: String): List<Long> = stubLastCorrectQids
        override suspend fun getLastIncorrectQids(dbName: String): List<Long> = stubLastIncorrectQids
        override suspend fun getEverCorrectQids(dbName: String): List<Long> = stubEverCorrectQids
        override suspend fun getEverIncorrectQids(dbName: String): List<Long> = stubEverIncorrectQids
    }

    private val stubMarkedDao = object : RoomMarkedQuestionDao {
        override suspend fun isMarked(dbName: String, questionId: Long): Boolean = stubMarkedQids.contains(questionId)
        override suspend fun getMarkedQuestionIds(dbName: String): List<Long> = stubMarkedQids
        override fun observeMarkedQuestionIds(dbName: String): Flow<List<Long>> = flowOf(stubMarkedQids)
        override suspend fun countMarked(dbName: String): Int = stubMarkedQids.size
        override suspend fun upsert(entity: MarkedQuestionEntity) = Unit
        override suspend fun delete(dbName: String, questionId: Long) = Unit
        override suspend fun clearForDatabase(dbName: String) = Unit
    }

    @BeforeTest
    fun setup() {
        connection = driver.open(":memory:")
        connection.prepare(
            """
            CREATE TABLE Questions (
                id INTEGER PRIMARY KEY,
                question TEXT,
                explanation TEXT,
                corrAns INTEGER,
                title TEXT,
                mediaName TEXT,
                otherMedias TEXT,
                pplTaken REAL,
                corrTaken REAL,
                subId TEXT,
                sysId TEXT
            )
            """.trimIndent()
        ).use { it.step() }

        val insertSql = """
            INSERT INTO Questions (id, question, explanation, corrAns, pplTaken, corrTaken, subId, sysId)
            VALUES 
                (1, 'Q1', 'Exp1', 1, 100.0, 80.0, '1', '10'),
                (2, 'Q2', 'Exp2', 2, 100.0, 40.0, '1', '10'),
                (3, 'Q3', 'Exp3', 1, 100.0, 10.0, '2', '20'),
                (4, 'Q4', 'Exp4', 1, 100.0, 90.0, '2', '20'),
                (5, 'Q5', 'Exp5', 2, 100.0, 60.0, '3', '30'),
                (6, 'Q6', 'Exp6', 1, 100.0, 20.0, '3', '30')
        """.trimIndent()
        connection.prepare(insertSql).use { it.step() }
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    private fun createDao() = QuestionDao(
        getConnection = { connection },
        mutex = mutex,
        isStringColumn = { true },
        getLogDao = { stubLogDao },
        getMarkedQuestionDao = { stubMarkedDao },
    )

    @Test
    fun filterByMarkedAloneReturnsOnlyMarkedQuestions() = runTest {
        val dao = createDao()
        stubMarkedQids = listOf(2L, 4L)

        val qids = dao.getQuestionIds(
            dbName = "test",
            subjectIds = null,
            systemIds = null,
            statusFilter = StatusFilter(setOf(QuestionStatus.MARKED))
        )

        assertEquals(listOf(2L, 4L), qids)
    }

    @Test
    fun filterByMarkedUnionIncorrectLatestAttempt() = runTest {
        val dao = createDao()
        stubMarkedQids = listOf(1L)
        stubLastIncorrectQids = listOf(3L)

        val qids = dao.getQuestionIds(
            dbName = "test",
            subjectIds = null,
            systemIds = null,
            statusFilter = StatusFilter(
                statuses = setOf(QuestionStatus.MARKED, QuestionStatus.ANSWERED_INCORRECTLY),
                scope = StatusScope.LATEST_ATTEMPT
            )
        )

        assertEquals(listOf(1L, 3L), qids)
    }

    @Test
    fun filterByMarkedUnionIncorrectAllAttempts() = runTest {
        val dao = createDao()
        stubMarkedQids = listOf(1L)
        stubEverIncorrectQids = listOf(2L, 3L)

        val qids = dao.getQuestionIds(
            dbName = "test",
            subjectIds = null,
            systemIds = null,
            statusFilter = StatusFilter(
                statuses = setOf(QuestionStatus.MARKED, QuestionStatus.ANSWERED_INCORRECTLY),
                scope = StatusScope.ALL_ATTEMPTS
            )
        )

        assertEquals(listOf(1L, 2L, 3L), qids)
    }

    @Test
    fun filterByMarkedUnionNotYetAnswered() = runTest {
        val dao = createDao()
        // Logged: 1, 2, 3 -> Unanswered: 4, 5, 6
        stubAllLoggedQids = listOf(1L, 2L, 3L)
        stubMarkedQids = listOf(1L)

        val qids = dao.getQuestionIds(
            dbName = "test",
            subjectIds = null,
            systemIds = null,
            statusFilter = StatusFilter(
                statuses = setOf(QuestionStatus.MARKED, QuestionStatus.NOT_YET_ANSWERED)
            )
        )

        // Union: 1 (marked) + 4, 5, 6 (unanswered)
        assertEquals(listOf(1L, 4L, 5L, 6L), qids)
    }

    @Test
    fun isAllStatusFilterFastPathReturnsAllQuestionsDirectly() = runTest {
        val dao = createDao()
        // If isAll is true, questions 1..6 returned directly without checking logged/marked
        val allAttemptStatusesFilter = StatusFilter(
            statuses = setOf(
                QuestionStatus.NOT_YET_ANSWERED,
                QuestionStatus.ANSWERED_INCORRECTLY,
                QuestionStatus.ANSWERED_CORRECTLY
            )
        )

        val qids = dao.getQuestionIds(
            dbName = "test",
            subjectIds = null,
            systemIds = null,
            statusFilter = allAttemptStatusesFilter
        )

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), qids)
    }
}
