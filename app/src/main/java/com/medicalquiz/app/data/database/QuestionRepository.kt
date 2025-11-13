package com.medicalquiz.app.data.database

import android.database.Cursor
import com.medicalquiz.app.data.models.Answer
import com.medicalquiz.app.data.models.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for question-related database operations
 */
class QuestionRepository(private val connection: DatabaseConnection) {
    
    /**
     * Get all question IDs, optionally filtered by subjects and systems
     */
    suspend fun getQuestionIds(
        subjectIds: List<Long>? = null,
        systemIds: List<Long>? = null
    ): List<Long> = withContext(Dispatchers.IO) {
        val db = connection.getDatabase()
        val questionIds = mutableListOf<Long>()
        
        var sql = "SELECT id FROM Questions ORDER BY id"
        val args = mutableListOf<String>()
        
        // Build WHERE conditions for comma-separated IDs
        if (!subjectIds.isNullOrEmpty() || !systemIds.isNullOrEmpty()) {
            val conditions = mutableListOf<String>()
            
            if (!subjectIds.isNullOrEmpty()) {
                val subConditions = subjectIds.joinToString(" OR ") { id ->
                    args.add(id.toString())
                    args.add("$id,%")
                    args.add("%,$id,%")
                    args.add("%,$id")
                    "(subId = ? OR subId LIKE ? OR subId LIKE ? OR subId LIKE ?)"
                }
                conditions.add("($subConditions)")
            }
            
            if (!systemIds.isNullOrEmpty()) {
                val sysConditions = systemIds.joinToString(" OR ") { id ->
                    args.add(id.toString())
                    args.add("$id,%")
                    args.add("%,$id,%")
                    args.add("%,$id")
                    "(sysId = ? OR sysId LIKE ? OR sysId LIKE ? OR sysId LIKE ?)"
                }
                conditions.add("($sysConditions)")
            }
            
            sql = "SELECT id FROM Questions WHERE ${conditions.joinToString(" AND ")} ORDER BY id"
        }
        
        val cursor = if (args.isEmpty()) {
            db.rawQuery(sql, null)
        } else {
            db.rawQuery(sql, args.toTypedArray())
        }
        
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
        val db = connection.getDatabase()
        
        // Handle comma-separated IDs - get first one
        val firstId = subId.split(",").firstOrNull()?.trim() ?: return null
        
        val cursor = db.rawQuery(
            "SELECT name FROM Subjects WHERE id = ?",
            arrayOf(firstId)
        )
        
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
    
    private fun getSystemName(sysId: String?): String? {
        if (sysId.isNullOrBlank()) return null
        val db = connection.getDatabase()
        
        // Handle comma-separated IDs - get first one
        val firstId = sysId.split(",").firstOrNull()?.trim() ?: return null
        
        val cursor = db.rawQuery(
            "SELECT name FROM Systems WHERE id = ?",
            arrayOf(firstId)
        )
        
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
}
