package com.medicalquiz.app.data.database

import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository for question-related database operations
 */
class QuestionRepository(private val connection: DatabaseConnection) {
    private val subjectNameCache = mutableMapOf<Long, String?>()
    private val systemNameCache = mutableMapOf<Long, String?>()
    private val questionQuery by lazy {
        connection.getDatabase().compileStatement(
            "SELECT id, question, explanation, corrAns, title, mediaName, otherMedias, pplTaken, corrTaken, subId, sysId FROM Questions WHERE id = ?"
        )
    }
    private val answersQuery by lazy {
        connection.getDatabase().compileStatement(
            "SELECT answerId, answerText, correctPercentage FROM Answers WHERE qId = ? ORDER BY answerId"
        )
    }
    // Guard compiled statements (like `questionQuery`) with a coroutine-friendly
    // mutex so access is non-blocking and cancellations behave well.
    private val compiledStatementMutex = Mutex()
    
    /**
     * Get all question IDs, optionally filtered by subjects and systems
     */
    suspend fun getQuestionIds(
        subjectIds: List<Long>? = null,
        systemIds: List<Long>? = null,
        performanceFilter: PerformanceFilter = PerformanceFilter.ALL
    ): List<Long> = withContext(Dispatchers.IO) {
        val db = connection.getDatabase()
        val questionIds = mutableListOf<Long>()
        val args = mutableListOf<String>()
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
            when (performanceFilter) {
                PerformanceFilter.ALL -> {}
                PerformanceFilter.UNANSWERED -> append(" LEFT JOIN logs_summary ls ON ls.qid = q.id")
                else -> append(" JOIN logs_summary ls ON ls.qid = q.id")
            }
            if (whereClauses.isNotEmpty()) {
                append(" WHERE ")
                append(whereClauses.joinToString(" AND "))
            }
            append(" ORDER BY q.id")
        }

        val cursor = db.rawQuery(sql, args.toTypedArray().takeIf { it.isNotEmpty() })
        cursor.use {
            while (it.moveToNext()) {
                questionIds.add(it.getLong(0))
            }
        }
        questionIds
    }
    
    /**
     * Get a specific question by ID
     */
    suspend fun getQuestionById(id: Long): Question? = withContext(Dispatchers.IO) {
        val db = connection.getDatabase()
        compiledStatementMutex.withLock {
            questionQuery.bindLong(1, id)
            // Execute the compiled statement to validate / warm the statement cache.
            // If there is no row for this id, `simpleQueryForLong()` will throw
            // SQLiteDoneException — catch it and continue; we'll still use the
            // rawQuery below to safely read the whole row (which returns null if
            // it does not exist).
            try {
                questionQuery.simpleQueryForLong()
            } catch (e: android.database.sqlite.SQLiteDoneException) {
                // No op — item not found
            } finally {
                questionQuery.clearBindings()
            }
        }

        // Since we need full row data, we still need to use rawQuery for now
        // But we can optimize the subject/system name fetching
        val fullCursor = db.rawQuery(
            "SELECT id, question, explanation, corrAns, title, mediaName, otherMedias, pplTaken, corrTaken, subId, sysId FROM Questions WHERE id = ?",
            arrayOf(id.toString())
        )

        fullCursor.use {
            if (it.moveToFirst()) {
                // Get subject and system names with optimized queries
                val subId = it.getString(9)
                val sysId = it.getString(10)
                // getSubjectName/getSystemName are now implemented with rawQuery
                // which is safe to call concurrently. No need to lock here.
                val subName = getSubjectName(subId)
                val sysName = getSystemName(sysId)

                Question(
                    id = it.getLong(0),
                    question = it.getString(1) ?: "",
                    explanation = it.getString(2) ?: "",
                    corrAns = it.getInt(3),
                    title = it.getString(4),
                    mediaName = it.getString(5),
                    otherMedias = it.getString(6),
                    pplTaken = if (it.isNull(7)) null else it.getDouble(7),
                    corrTaken = if (it.isNull(8)) null else it.getDouble(8),
                    subId = subId,
                    sysId = sysId,
                    subName = subName,
                    sysName = sysName
                )
            } else {
                null
            }
        }
    }
    
    /**
     * Get answers for a specific question
     */
    suspend fun getAnswersForQuestion(questionId: Long): List<Answer> = withContext(Dispatchers.IO) {
        val db = connection.getDatabase()
        val answers = ArrayList<Answer>()

        // Use prepared statement approach for better performance
        val cursor = db.rawQuery(
            "SELECT answerId, answerText, correctPercentage FROM Answers WHERE qId = ? ORDER BY answerId",
            arrayOf(questionId.toString())
        )

        cursor.use {
            // Pre-allocate capacity if possible
            val count = it.count
            if (count > 0) answers.ensureCapacity(count)

            while (it.moveToNext()) {
                answers.add(
                    Answer(
                        answerId = it.getLong(0),
                        answerText = it.getString(1) ?: "",
                        correctPercentage = if (it.isNull(2)) null else it.getInt(2),
                        qId = questionId
                    )
                )
            }
        }

        answers
    }
    
    private fun getSubjectName(subId: String?): String? {
        if (subId.isNullOrBlank()) return null
        
        val firstId = extractFirstId(subId) ?: return null
        return subjectNameCache[firstId] ?: fetchSubjectName(firstId).also { subjectNameCache[firstId] = it }
    }
    
    private fun getSystemName(sysId: String?): String? {
        if (sysId.isNullOrBlank()) return null
        val firstId = extractFirstId(sysId) ?: return null
        return systemNameCache[firstId] ?: fetchSystemName(firstId).also { systemNameCache[firstId] = it }
    }

    private fun buildMultiValueCondition(
        columnAlias: String,
        ids: List<Long>,
        args: MutableList<String>
    ): String {
        val normalizedIds = ids.distinct()
        return when (normalizedIds.size) {
            0 -> "1=1" // Should not happen, but safe fallback
            1 -> {
                args.add(normalizedIds[0].toString())
                "instr(',' || $columnAlias || ',', ',' || ? || ',') > 0"
            }
            else -> {
                // For multiple values, create an efficient OR condition
                val conditions = normalizedIds.map { id ->
                    args.add(id.toString())
                    "instr(',' || $columnAlias || ',', ',' || ? || ',') > 0"
                }
                "(${conditions.joinToString(" OR ")})"
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

    private fun extractFirstId(rawIds: String?): Long? = rawIds
        ?.split(",")
        ?.firstOrNull()
        ?.trim()
        ?.toLongOrNull()

    private fun fetchSubjectName(id: Long): String? {
        val db = connection.getDatabase()
        val cur = db.rawQuery("SELECT name FROM Subjects WHERE id = ?", arrayOf(id.toString()))
        cur.use {
            return if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun fetchSystemName(id: Long): String? {
        val db = connection.getDatabase()
        val cur = db.rawQuery("SELECT name FROM Systems WHERE id = ?", arrayOf(id.toString()))
        cur.use {
            return if (it.moveToFirst()) it.getString(0) else null
        }
    }
}
