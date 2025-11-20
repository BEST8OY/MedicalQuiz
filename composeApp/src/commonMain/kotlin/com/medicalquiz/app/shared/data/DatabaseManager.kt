package com.medicalquiz.app.shared.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medicalquiz.app.shared.data.database.DatabaseProvider
import com.medicalquiz.app.shared.data.database.PerformanceFilter
import com.medicalquiz.app.shared.data.database.QuestionPerformance
import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.data.models.Question
import com.medicalquiz.app.shared.data.models.Subject
import com.medicalquiz.app.shared.data.models.System
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class DatabaseManager(private val dbPath: String) : DatabaseProvider {
    private val driver = BundledSQLiteDriver()
    private var connection: SQLiteConnection? = null
    private val mutex = Mutex()
    private var isStringIds: Boolean = true

    suspend fun init() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                connection = driver.open(dbPath)
                checkSchema()
            } catch (e: Exception) {
                println("Error initializing database: ${e.message}")
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
            println("Schema check failed: ${e.message}")
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
    ): List<Long> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val args = mutableListOf<Any>()
            val whereClauses = mutableListOf<String>()

            subjectIds?.takeIf { it.isNotEmpty() }?.let {
                whereClauses.add(buildMultiValueCondition("q.subId", it, args))
            }

            systemIds?.takeIf { it.isNotEmpty() }?.let {
                whereClauses.add(buildMultiValueCondition("q.sysId", it, args))
            }

            buildPerformanceClause(performanceFilter)?.let { whereClauses.add(it) }

            val sql = buildString {
                append("SELECT q.id FROM Questions q")
                
                // Join with logs summary if needed for performance filtering
                if (performanceFilter != PerformanceFilter.ALL) {
                    append(" LEFT JOIN (")
                    append("   SELECT qid, ")
                    append("     (CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as lastCorrect,")
                    append("     MAX(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as everCorrect,")
                    append("     MAX(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as everIncorrect")
                    append("   FROM logs")
                    append("   GROUP BY qid")
                    append(" ) ls ON ls.qid = q.id")
                }

                if (whereClauses.isNotEmpty()) {
                    append(" WHERE ")
                    append(whereClauses.joinToString(" AND "))
                }
                append(" ORDER BY q.id")
            }

            val result = mutableListOf<Long>()
            getConnection().prepare(sql).use { stmt ->
                bindArgs(stmt, args)
                while (stmt.step()) {
                    result.add(stmt.getLong(0))
                }
            }
            result
        }
    }

    override suspend fun getQuestionById(id: Long): Question? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                SELECT id, question, explanation, corrAns, title, mediaName, otherMedias, 
                       pplTaken, corrTaken, subId, sysId 
                FROM Questions WHERE id = ?
            """
            
            var question: Question? = null
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, id)
                if (stmt.step()) {
                    val subIdStr = if (stmt.isNull(9)) null else stmt.getText(9)
                    val sysIdStr = if (stmt.isNull(10)) null else stmt.getText(10)
                    
                    val subName = subIdStr?.let { getSubjectNames(it) }
                    val sysName = sysIdStr?.let { getSystemNames(it) }

                    question = Question(
                        id = stmt.getLong(0),
                        question = if (stmt.isNull(1)) "" else stmt.getText(1),
                        explanation = if (stmt.isNull(2)) "" else stmt.getText(2),
                        corrAns = if (stmt.isNull(3)) -1 else stmt.getLong(3).toInt(),
                        title = if (stmt.isNull(4)) null else stmt.getText(4),
                        mediaName = if (stmt.isNull(5)) null else stmt.getText(5),
                        otherMedias = if (stmt.isNull(6)) null else stmt.getText(6),
                        pplTaken = if (stmt.isNull(7)) null else stmt.getDouble(7),
                        corrTaken = if (stmt.isNull(8)) null else stmt.getDouble(8),
                        subId = subIdStr,
                        sysId = sysIdStr,
                        subName = subName,
                        sysName = sysName
                    )
                }
            }
            question
        }
    }

    private fun getSubjectNames(idsStr: String): String {
        val ids = idsStr.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return ""
        
        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT name FROM Subjects WHERE id IN ($placeholders)"
        
        val names = mutableListOf<String>()
        getConnection().prepare(sql).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
            while (stmt.step()) {
                if (!stmt.isNull(0)) {
                    names.add(stmt.getText(0))
                }
            }
        }
        return names.joinToString(", ")
    }

    private fun getSystemNames(idsStr: String): String {
        val ids = idsStr.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return ""
        
        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT name FROM Systems WHERE id IN ($placeholders)"
        
        val names = mutableListOf<String>()
        getConnection().prepare(sql).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
            while (stmt.step()) {
                if (!stmt.isNull(0)) {
                    names.add(stmt.getText(0))
                }
            }
        }
        return names.joinToString(", ")
    }

    override suspend fun getAnswersForQuestion(questionId: Long): List<Answer> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = "SELECT id, answerId, answerText, correctPercentage, qId FROM Answers WHERE qId = ?"
            val answers = mutableListOf<Answer>()
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, questionId)
                while (stmt.step()) {
                    answers.add(Answer(
                        answerId = if (stmt.isNull(1)) stmt.getLong(0) else stmt.getLong(1),
                        answerText = if (stmt.isNull(2)) "" else stmt.getText(2),
                        correctPercentage = if (stmt.isNull(3)) null else stmt.getLong(3).toInt(),
                        qId = if (stmt.isNull(4)) -1L else stmt.getLong(4)
                    ))
                }
            }
            answers
        }
    }

    override suspend fun getSubjects(): List<Subject> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = "SELECT id, name, count FROM Subjects ORDER BY name"
            val subjects = mutableListOf<Subject>()
            getConnection().prepare(sql).use { stmt ->
                while (stmt.step()) {
                    subjects.add(Subject(
                        id = stmt.getLong(0),
                        name = if (stmt.isNull(1)) "" else stmt.getText(1),
                        count = if (stmt.isNull(2)) 0 else stmt.getLong(2).toInt()
                    ))
                }
            }
            subjects
        }
    }

    override suspend fun getSystems(subjectIds: List<Long>?): List<System> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val systems = mutableListOf<System>()
            
            if (subjectIds.isNullOrEmpty()) {
                val sql = "SELECT id, name, count FROM Systems ORDER BY name"
                getConnection().prepare(sql).use { stmt ->
                    while (stmt.step()) {
                        systems.add(System(
                            id = stmt.getLong(0),
                            name = if (stmt.isNull(1)) "" else stmt.getText(1),
                            count = if (stmt.isNull(2)) 0 else stmt.getLong(2).toInt()
                        ))
                    }
                }
            } else {
                // Get system IDs from SubjectsSystems
                val placeholders = subjectIds.joinToString(",") { "?" }
                val sysIdSql = "SELECT DISTINCT sysId FROM SubjectsSystems WHERE subId IN ($placeholders)"
                val sysIds = mutableListOf<Long>()
                
                getConnection().prepare(sysIdSql).use { stmt ->
                    subjectIds.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
                    while (stmt.step()) {
                        sysIds.add(stmt.getLong(0))
                    }
                }
                
                if (sysIds.isNotEmpty()) {
                    val sysPlaceholders = sysIds.joinToString(",") { "?" }
                    val sql = "SELECT id, name, count FROM Systems WHERE id IN ($sysPlaceholders) ORDER BY name"
                    getConnection().prepare(sql).use { stmt ->
                        sysIds.forEachIndexed { index, id -> stmt.bindLong(index + 1, id) }
                        while (stmt.step()) {
                            systems.add(System(
                                id = stmt.getLong(0),
                                name = if (stmt.isNull(1)) "" else stmt.getText(1),
                                count = if (stmt.isNull(2)) 0 else stmt.getLong(2).toInt()
                            ))
                        }
                    }
                }
            }
            systems
        }
    }

    override suspend fun logAnswer(
        qid: Long,
        selectedAnswer: Int,
        corrAnswer: Int,
        time: Long,
        testId: String
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Clock.System.now()
            val dateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val dateString = "${dateTime.year}-${dateTime.monthNumber.toString().padStart(2, '0')}-${dateTime.dayOfMonth.toString().padStart(2, '0')} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}:${dateTime.second.toString().padStart(2, '0')}"
            
            val sql = "INSERT INTO logs (qid, selectedAnswer, corrAnswer, time, answerDate, testId) VALUES (?, ?, ?, ?, ?, ?)"
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, qid)
                stmt.bindLong(2, selectedAnswer.toLong())
                stmt.bindLong(3, corrAnswer.toLong())
                stmt.bindLong(4, time)
                stmt.bindText(5, dateString)
                val testIdLong = testId.toLongOrNull()
                if (testIdLong != null) {
                    stmt.bindLong(6, testIdLong)
                } else {
                    stmt.bindNull(6)
                }
                stmt.step()
            }
            Unit
        }
    }

    override suspend fun clearLogs() = withContext(Dispatchers.IO) {
        mutex.withLock {
            getConnection().prepare("DELETE FROM logs").use { stmt ->
                stmt.step()
            }
            Unit
        }
    }

    override suspend fun getQuestionPerformance(qid: Long): QuestionPerformance? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                SELECT 
                   (CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as lastCorrect,
                   MAX(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as everCorrect,
                   MAX(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as everIncorrect,
                   COUNT(*) as attempts,
                   SUM(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as correctCount,
                   SUM(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as incorrectCount
                FROM logs
                WHERE qid = ?
                GROUP BY qid
            """
            
            var performance: QuestionPerformance? = null
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, qid)
                if (stmt.step()) {
                    performance = QuestionPerformance(
                        qid = qid,
                        lastCorrect = stmt.getLong(0) == 1L,
                        everCorrect = stmt.getLong(1) == 1L,
                        everIncorrect = stmt.getLong(2) == 1L,
                        attempts = stmt.getLong(3).toInt(),
                        correctCount = stmt.getLong(4).toInt(),
                        incorrectCount = stmt.getLong(5).toInt()
                    )
                }
            }
            performance
        }
    }

    private fun buildMultiValueCondition(
        columnAlias: String,
        ids: List<Long>,
        args: MutableList<Any>
    ): String {
        val normalizedIds = ids.distinct()
        if (normalizedIds.isEmpty()) return "1=1"

        if (!isStringIds) {
            // Integer IDs: use IN clause
            val placeholders = normalizedIds.joinToString(",") { "?" }
            args.addAll(normalizedIds)
            return "$columnAlias IN ($placeholders)"
        } else {
            // String IDs (comma separated): use instr
            return when (normalizedIds.size) {
                1 -> {
                    args.add(normalizedIds[0].toString())
                    "instr(',' || $columnAlias || ',', ',' || ? || ',') > 0"
                }
                else -> {
                    val conditions = normalizedIds.map { id ->
                        args.add(id.toString())
                        "instr(',' || $columnAlias || ',', ',' || ? || ',') > 0"
                    }
                    "(${conditions.joinToString(" OR ")})"
                }
            }
        }
    }

    private fun buildPerformanceClause(filter: PerformanceFilter): String? = when (filter) {
        PerformanceFilter.ALL -> null
        PerformanceFilter.UNANSWERED -> "ls.qid IS NULL"
        PerformanceFilter.LAST_CORRECT -> "ls.lastCorrect = 1"
        PerformanceFilter.LAST_INCORRECT -> "ls.lastCorrect = 0"
        PerformanceFilter.EVER_CORRECT -> "ls.everCorrect = 1"
        PerformanceFilter.EVER_INCORRECT -> "ls.everIncorrect = 1"
    }

    private fun bindArgs(stmt: SQLiteStatement, args: List<Any>) {
        args.forEachIndexed { index, arg ->
            val bindIndex = index + 1
            when (arg) {
                is String -> stmt.bindText(bindIndex, arg)
                is Long -> stmt.bindLong(bindIndex, arg)
                is Int -> stmt.bindLong(bindIndex, arg.toLong())
                is Double -> stmt.bindDouble(bindIndex, arg)
                is Float -> stmt.bindDouble(bindIndex, arg.toDouble())
                is Boolean -> stmt.bindLong(bindIndex, if (arg) 1L else 0L)
                else -> stmt.bindText(bindIndex, arg.toString())
            }
        }
    }
}
