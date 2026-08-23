package com.medqb.app.shared.data.dao

import androidx.sqlite.SQLiteConnection
import com.medqb.app.shared.data.database.PerformanceFilter
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
    private val getLogDao: suspend () -> RoomLogDao,
) {
    suspend fun getQuestionIds(
        dbName: String,
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val perfMatcher = resolvePerformanceMatcher(dbName, performanceFilter)

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
                    if (perfMatcher == null || perfMatcher(qid)) {
                        result.add(qid)
                    }
                }
            }
            result
        }
    }

    suspend fun getQuestionById(id: Long): Question? = withContext(Dispatchers.IO) {
        mutex.withLock { getQuestionByIdInternal(id) }
    }

    suspend fun getAnswersForQuestion(questionId: Long): List<Answer> = withContext(Dispatchers.IO) {
        mutex.withLock { getAnswersForQuestionInternal(questionId) }
    }

    /**
     * Question + answers fetched under a single lock acquisition.
     */
    suspend fun getQuestionWithDetails(
        questionId: Long,
    ): Pair<Question?, List<Answer>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val question = getQuestionByIdInternal(questionId)
            val answers = if (question != null) getAnswersForQuestionInternal(questionId) else emptyList()
            question to answers
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

                val subName = subIdStr?.let { lookupNamesUnderLock(it, "Subjects") }
                val sysName = sysIdStr?.let { lookupNamesUnderLock(it, "Systems") }

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
        // ORDER BY id gives a deterministic position for Question.corrAns (1-based index).
        val sql = "SELECT id, answerId, answerText, correctPercentage, qId FROM Answers WHERE qId = ? ORDER BY id"
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

    /**
     * Preview count for the given filters. Deliberately derived from [getQuestionIds]
     * rather than a second query: the count feeds the exact selection the quiz will
     * load, so both must share one query path and can never drift apart.
     */
    suspend fun countQuestionIds(
        dbName: String,
        subjectIds: List<Long>?,
        systemIds: List<Long>?,
        performanceFilter: PerformanceFilter,
    ): Int = getQuestionIds(dbName, subjectIds, systemIds, performanceFilter).size

    /**
     * Returns a predicate selecting question ids that satisfy [performanceFilter] according
     * to the user's logs, or `null` when every question matches ([PerformanceFilter.ALL]).
     * The filter is resolved entirely in SQL — each query returns exactly the matching qids.
     */
    private suspend fun resolvePerformanceMatcher(
        dbName: String,
        performanceFilter: PerformanceFilter
    ): ((Long) -> Boolean)? {
        val logDao = getLogDao()
        return when (performanceFilter) {
            PerformanceFilter.ALL -> null
            PerformanceFilter.UNANSWERED -> notIn(logDao.getAllLoggedQids(dbName))
            PerformanceFilter.LAST_CORRECT -> inSet(logDao.getLastCorrectQids(dbName))
            PerformanceFilter.LAST_INCORRECT -> inSet(logDao.getLastIncorrectQids(dbName))
            PerformanceFilter.EVER_CORRECT -> inSet(logDao.getEverCorrectQids(dbName))
            PerformanceFilter.EVER_INCORRECT -> inSet(logDao.getEverIncorrectQids(dbName))
        }
    }

    private fun inSet(qids: List<Long>): (Long) -> Boolean {
        val set = qids.toSet()
        return { qid -> qid in set }
    }

    private fun notIn(qids: List<Long>): (Long) -> Boolean {
        val set = qids.toSet()
        return { qid -> qid !in set }
    }

    /**
     * Look up display names for a comma-separated id string against [table]
     * ("Subjects" or "Systems"). Only callable from code already holding [mutex] —
     * it executes SQL directly so no path can touch the connection unlocked.
     */
    private fun lookupNamesUnderLock(idsStr: String, table: String): String {
        val ids = idsStr.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return ""

        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT name FROM $table WHERE id IN ($placeholders)"

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
