package com.medicalquiz.app.data.database

import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for question-related database operations
 */
class QuestionRepository(private val connection: DatabaseConnection) {
    private val subjectNameCache = mutableMapOf<Long, String?>()
    private val systemNameCache = mutableMapOf<Long, String?>()
    
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
        val cursor = db.rawQuery(
            "SELECT id, question, explanation, corrAns, title, mediaName, otherMedias, pplTaken, corrTaken, subId, sysId FROM Questions WHERE id = ?",
            arrayOf(id.toString())
        )
        
        cursor.use {
            if (it.moveToFirst()) {
                // Get subject and system names if available
                val subId = it.getString(9)
                val sysId = it.getString(10)
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
        val answers = mutableListOf<Answer>()
        
        val cursor = db.rawQuery(
            "SELECT answerId, answerText, correctPercentage FROM Answers WHERE qId = ? ORDER BY answerId",
            arrayOf(questionId.toString())
        )
        
        cursor.use {
            while (it.moveToNext()) {
                answers.add(
                    Answer(
                        answerId = it.getLong(0),
                        answerText = it.getString(1) ?: "",
                        correctPercentage = if (it.isNull(2)) null else it.getInt(2)
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
        return normalizedIds.joinToString(" OR ") { id ->
            args.add(id.toString())
            "instr(',' || COALESCE($columnAlias, '') || ',', ',' || ? || ',') > 0"
        }.let { "($it)" }
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
        val cursor = connection.getDatabase().rawQuery("SELECT name FROM Subjects WHERE id = ?", arrayOf(id.toString()))
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun fetchSystemName(id: Long): String? {
        val cursor = connection.getDatabase().rawQuery("SELECT name FROM Systems WHERE id = ?", arrayOf(id.toString()))
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }
}
