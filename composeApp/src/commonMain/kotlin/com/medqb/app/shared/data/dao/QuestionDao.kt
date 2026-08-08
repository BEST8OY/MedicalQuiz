package com.medqb.app.shared.data.dao

import androidx.sqlite.SQLiteConnection
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.database.QuestionPerformance
import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.models.Answer
import com.medqb.app.shared.data.models.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class QuestionDao(
    private val getConnection: () -> SQLiteConnection,
    private val mutex: Mutex,
    private val isStringIds: () -> Boolean,
    private val getSubjectNames: (String) -> String,
    private val getSystemNames: (String) -> String,
    private val roomLogDao: RoomLogDao,
) {
    suspend fun getQuestionIds(
        dbName: String,
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        mutex.withLock {
            // For performance filters, get logged qids from Room first
            val loggedQids = if (performanceFilter != PerformanceFilter.ALL) {
                roomLogDao.getAllLoggedQids(dbName).toSet()
            } else emptySet()

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
                    val qid = stmt.getLong(0)
                    // Apply performance filter using Room data
                    if (performanceFilter == PerformanceFilter.ALL || qid in loggedQids) {
                        result.add(qid)
                    }
                }
            }

            // For now, return all matching qids (performance filtering is approximate)
            // A more precise implementation would query Room for each qid's performance
            if (performanceFilter == PerformanceFilter.ALL) {
                result
            } else {
                result.takeIf { it.isNotEmpty() } ?: emptyList()
            }
        }
    }

    suspend fun getQuestionById(id: Long): Question? = withContext(Dispatchers.IO) {
        mutex.withLock { getQuestionByIdInternal(id) }
    }

    suspend fun getAnswersForQuestion(questionId: Long): List<Answer> = withContext(Dispatchers.IO) {
        mutex.withLock { getAnswersForQuestionInternal(questionId) }
    }

    suspend fun getQuestionWithDetails(
        questionId: Long,
    ): Triple<Question?, List<Answer>, QuestionPerformance?> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val question = getQuestionByIdInternal(questionId)
            val answers = if (question != null) getAnswersForQuestionInternal(questionId) else emptyList()
            Triple(question, answers, null)
        }
    }

    private fun getQuestionByIdInternal(id: Long): Question? {
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
        return question
    }

    private fun getAnswersForQuestionInternal(questionId: Long): List<Answer> {
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
        return answers
    }

    suspend fun countQuestionIds(
        dbName: String,
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter,
    ): Int = withContext(Dispatchers.IO) {
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
                append("SELECT COUNT(*) FROM Questions q")
                if (whereClauses.isNotEmpty()) {
                    append(" WHERE ")
                    append(whereClauses.joinToString(" AND "))
                }
            }

            getConnection().prepare(sql).use { stmt ->
                bindArgs(stmt, args)
                if (stmt.step()) stmt.getLong(0).toInt() else 0
            }
        }
    }

    private fun buildMultiValueCondition(
        columnAlias: String,
        ids: List<Long>,
        args: MutableList<Any>
    ): String {
        val normalizedIds = ids.distinct()
        if (normalizedIds.isEmpty()) return "1=1"

        if (!isStringIds()) {
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

    private fun bindArgs(stmt: androidx.sqlite.SQLiteStatement, args: List<Any>) {
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
