package com.medqb.app.shared.data.dao

import androidx.sqlite.SQLiteConnection
import com.medqb.app.shared.data.database.PerformanceFilter
import com.medqb.app.shared.data.database.QuestionPerformance
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
) {
    suspend fun getQuestionIds(
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

                if (performanceFilter != PerformanceFilter.ALL) {
                    append(" LEFT JOIN (")
                    append("   SELECT l.qid,")
                    append("     (CASE WHEN l.selectedAnswer = l.corrAnswer THEN 1 ELSE 0 END) as lastCorrect,")
                    append("     agg.everCorrect,")
                    append("     agg.everIncorrect")
                    append("   FROM logs l")
                    append("   JOIN (")
                    append("     SELECT qid,")
                    append("       MAX(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as everCorrect,")
                    append("       MAX(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as everIncorrect,")
                    append("       MAX(rowid) as lastRowId")
                    append("     FROM logs")
                    append("     GROUP BY qid")
                    append("   ) agg ON agg.qid = l.qid AND agg.lastRowId = l.rowid")
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

    suspend fun getQuestionById(id: Long): Question? = withContext(Dispatchers.IO) {
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

    suspend fun getAnswersForQuestion(questionId: Long): List<Answer> = withContext(Dispatchers.IO) {
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

    suspend fun getQuestionPerformance(qid: Long): QuestionPerformance? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sql = """
                SELECT
                   latest.lastCorrect,
                   agg.everCorrect,
                   agg.everIncorrect,
                   agg.attempts,
                   agg.correctCount,
                   agg.incorrectCount
                FROM (
                    SELECT
                        qid,
                        (CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as lastCorrect
                    FROM logs
                    WHERE qid = ?
                    ORDER BY rowid DESC
                    LIMIT 1
                ) latest
                JOIN (
                    SELECT
                        qid,
                        MAX(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as everCorrect,
                        MAX(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as everIncorrect,
                        COUNT(*) as attempts,
                        SUM(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as correctCount,
                        SUM(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as incorrectCount
                    FROM logs
                    WHERE qid = ?
                    GROUP BY qid
                ) agg ON agg.qid = latest.qid
            """

            var performance: QuestionPerformance? = null
            getConnection().prepare(sql).use { stmt ->
                stmt.bindLong(1, qid)
                stmt.bindLong(2, qid)
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

    private fun buildPerformanceClause(filter: PerformanceFilter): String? = when (filter) {
        PerformanceFilter.ALL -> null
        PerformanceFilter.UNANSWERED -> "ls.qid IS NULL"
        PerformanceFilter.LAST_CORRECT -> "ls.lastCorrect = 1"
        PerformanceFilter.LAST_INCORRECT -> "ls.lastCorrect = 0"
        PerformanceFilter.EVER_CORRECT -> "ls.everCorrect = 1"
        PerformanceFilter.EVER_INCORRECT -> "ls.everIncorrect = 1"
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
