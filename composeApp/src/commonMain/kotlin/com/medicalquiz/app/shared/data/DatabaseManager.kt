package com.medicalquiz.app.shared.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.medicalquiz.app.shared.data.database.DatabaseProvider
import com.medicalquiz.app.shared.data.models.Answer
import com.medicalquiz.app.shared.data.models.Question
import com.medicalquiz.app.shared.data.models.Subject
import com.medicalquiz.app.shared.data.models.System
import com.medicalquiz.app.shared.platform.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DatabaseManager(private val dbPath: String) : DatabaseProvider {
    private val driver = BundledSQLiteDriver()
    private var connection: SQLiteConnection? = null
    private val mutex = Mutex()
    private var isStringIds: Boolean = true

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

            val sql = buildString {
                append("SELECT q.id FROM Questions q")

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

    private fun buildMultiValueCondition(
        columnAlias: String,
        ids: List<Long>,
        args: MutableList<Any>
    ): String {
        val normalizedIds = ids.distinct()
        if (normalizedIds.isEmpty()) return "1=1"

        if (!isStringIds) {
            val placeholders = normalizedIds.joinToString(",") { "?" }
            args.addAll(normalizedIds)
            return "$columnAlias IN ($placeholders)"
        } else {
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
