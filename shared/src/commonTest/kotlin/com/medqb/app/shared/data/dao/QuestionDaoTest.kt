package com.medqb.app.shared.data.dao

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medqb.app.shared.data.database.StatusFilter
import com.medqb.app.shared.data.local.dao.QuestionPerformanceResult
import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.local.entity.LogEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuestionDaoTest {

    private lateinit var connection: SQLiteConnection
    private val mutex = Mutex()
    private val driver = BundledSQLiteDriver()

    private val stubLogDao = object : RoomLogDao {
        override suspend fun insert(log: LogEntity): Long = 1L
        override suspend fun clearForQuestion(dbName: String, qid: Long) = Unit
        override suspend fun getLogRowIds(dbName: String, qid: Long): List<Long> = emptyList()
        override suspend fun getQuestionPerformance(dbName: String, qid: Long): QuestionPerformanceResult? = null
        override suspend fun getAllLoggedQids(dbName: String): List<Long> = emptyList()
        override suspend fun getLastCorrectQids(dbName: String): List<Long> = emptyList()
        override suspend fun getLastIncorrectQids(dbName: String): List<Long> = emptyList()
        override suspend fun getEverCorrectQids(dbName: String): List<Long> = emptyList()
        override suspend fun getEverIncorrectQids(dbName: String): List<Long> = emptyList()
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

        connection.prepare(
            """
            CREATE TABLE Answers (
                id INTEGER PRIMARY KEY,
                answerId INTEGER,
                answerText TEXT,
                correctPercentage INTEGER,
                qId INTEGER
            )
            """.trimIndent()
        ).use { it.step() }

        connection.prepare(
            """
            CREATE TABLE Subjects (
                id INTEGER PRIMARY KEY,
                name TEXT
            )
            """.trimIndent()
        ).use { it.step() }

        connection.prepare(
            """
            CREATE TABLE Systems (
                id INTEGER PRIMARY KEY,
                name TEXT
            )
            """.trimIndent()
        ).use { it.step() }

        connection.prepare(
            """
            INSERT INTO Subjects (id, name) VALUES 
                (1, 'Cardiology'),
                (2, 'Pulmonology'),
                (3, 'Neurology'),
                (10, 'Oncology')
            """.trimIndent()
        ).use { it.step() }

        connection.prepare(
            """
            INSERT INTO Systems (id, name) VALUES 
                (10, 'Cardiovascular'),
                (20, 'Respiratory')
            """.trimIndent()
        ).use { it.step() }

        // Insert test rows:
        // q1: subId "1, 2" (with space), sysId "10", 80% correct
        // q2: subId "2", sysId "20", 40% correct
        // q3: subId "3", sysId "10, 20", 10% correct
        // q4: subId "10" (tests substring protection against '1'), sysId "20", 90% correct
        // q5: subId "1", sysId "10", 60% correct
        // q6: subId "3", sysId "10", 20% correct
        val insertSql = """
            INSERT INTO Questions (id, question, explanation, corrAns, pplTaken, corrTaken, subId, sysId)
            VALUES 
                (1, 'Q1', 'Exp1', 1, 100.0, 80.0, '1, 2', '10'),
                (2, 'Q2', 'Exp2', 2, 100.0, 40.0, '2', '20'),
                (3, 'Q3', 'Exp3', 1, 100.0, 10.0, '3', '10, 20'),
                (4, 'Q4', 'Exp4', 1, 100.0, 90.0, '10', '20'),
                (5, 'Q5', 'Exp5', 2, 100.0, 60.0, '1', '10'),
                (6, 'Q6', 'Exp6', 1, 100.0, 20.0, '3', '10')
        """.trimIndent()
        connection.prepare(insertSql).use { it.step() }
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    @Test
    fun stringCsvMatchingHandlesWhitespaceCorrectly() = runTest {
        val dao = QuestionDao(
            getConnection = { connection },
            mutex = mutex,
            isStringColumn = { true },
            getLogDao = { stubLogDao }
        )

        // Searching for subject 2 must match q1 ('1, 2' with whitespace) and q2 ('2')
        val qids = dao.getQuestionIds(
            dbName = "test",
            subjectIds = listOf(2L),
            systemIds = null,
            statusFilter = StatusFilter.ALL
        )

        assertEquals(listOf(1L, 2L), qids)
    }

    @Test
    fun stringCsvMatchingPreventsSubstringFalsePositives() = runTest {
        val dao = QuestionDao(
            getConnection = { connection },
            mutex = mutex,
            isStringColumn = { true },
            getLogDao = { stubLogDao }
        )

        // Searching for subject 1 must match q1 ('1, 2') and q5 ('1'), but NOT q4 ('10')
        val qids = dao.getQuestionIds(
            dbName = "test",
            subjectIds = listOf(1L),
            systemIds = null,
            statusFilter = StatusFilter.ALL
        )

        assertEquals(listOf(1L, 5L), qids)

        // Searching for subject 10 matches only q4 ('10')
        val qids10 = dao.getQuestionIds(
            dbName = "test",
            subjectIds = listOf(10L),
            systemIds = null,
            statusFilter = StatusFilter.ALL
        )

        assertEquals(listOf(4L), qids10)
    }

    @Test
    fun stringCsvExclusionFiltersProperly() = runTest {
        val dao = QuestionDao(
            getConnection = { connection },
            mutex = mutex,
            isStringColumn = { true },
            getLogDao = { stubLogDao }
        )

        // Excluding subject 1 should eliminate q1 ('1, 2') and q5 ('1'), leaving q2, q3, q4, q6
        val qids = dao.getQuestionIds(
            dbName = "test",
            subjectIds = null,
            excludedSubjectIds = listOf(1L),
            systemIds = null,
            statusFilter = StatusFilter.ALL
        )

        assertEquals(listOf(2L, 3L, 4L, 6L), qids)
    }

    @Test
    fun initDifficultyIndexLoadsStatsAndCachesNames() = runTest {
        val dao = QuestionDao(
            getConnection = { connection },
            mutex = mutex,
            isStringColumn = { true },
            getLogDao = { stubLogDao }
        )

        dao.initDifficultyIndex()
        assertTrue(dao.isDifficultyAvailable())

        val q1 = dao.getQuestionById(1L)
        assertNotNull(q1)
        assertEquals("Q1", q1.question)
        assertNotNull(q1.difficultyTier)
        assertEquals("Cardiology, Pulmonology", q1.subName)
        assertEquals("Cardiovascular", q1.sysName)
    }
}
