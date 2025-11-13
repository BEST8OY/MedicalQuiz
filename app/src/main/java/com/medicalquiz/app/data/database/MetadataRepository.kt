package com.medicalquiz.app.data.database

import com.medicalquiz.app.data.models.Subject
import com.medicalquiz.app.data.models.System as QuizSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for metadata (subjects and systems)
 */
class MetadataRepository(private val connection: DatabaseConnection) {
    
    /**
     * Get all subjects
     */
    suspend fun getSubjects(): List<Subject> = withContext(Dispatchers.IO) {
        val db = connection.getDatabase()
        val subjects = mutableListOf<Subject>()
        
        val cursor = db.rawQuery(
            "SELECT id, name, count FROM Subjects ORDER BY name",
            null
        )
        
        cursor.use {
            while (it.moveToNext()) {
                subjects.add(
                    Subject(
                        id = it.getLong(0),
                        name = it.getString(1) ?: "",
                        count = if (it.isNull(2)) 0 else it.getInt(2)
                    )
                )
            }
        }
        
        subjects
    }
    
    /**
     * Get systems, optionally filtered by subjects
     */
    suspend fun getSystems(subjectIds: List<Long>? = null): List<QuizSystem> = withContext(Dispatchers.IO) {
        val db = connection.getDatabase()
        val systems = mutableListOf<QuizSystem>()
        
        val sql = if (!subjectIds.isNullOrEmpty()) {
            val args = mutableListOf<String>()
            val subConditions = subjectIds.joinToString(" OR ") { id ->
                args.add(id.toString())
                args.add("$id,%")
                args.add("%,$id,%")
                args.add("%,$id")
                "(subId = ? OR subId LIKE ? OR subId LIKE ? OR subId LIKE ?)"
            }
            
            val cursor = db.rawQuery(
                "SELECT DISTINCT sysId FROM Questions WHERE $subConditions",
                args.toTypedArray()
            )
            
            val systemIds = mutableSetOf<Long>()
            cursor.use {
                while (it.moveToNext()) {
                    val sysIdStr = it.getString(0)
                    if (!sysIdStr.isNullOrBlank()) {
                        sysIdStr.split(",").forEach { id ->
                            id.trim().toLongOrNull()?.let { systemIds.add(it) }
                        }
                    }
                }
            }
            
            if (systemIds.isEmpty()) {
                return@withContext emptyList()
            }
            
            val placeholders = systemIds.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT id, name, count FROM Systems WHERE id IN ($placeholders) ORDER BY name",
                systemIds.map { it.toString() }.toTypedArray()
            )
        } else {
            db.rawQuery("SELECT id, name, count FROM Systems ORDER BY name", null)
        }
        
        sql.use {
            while (it.moveToNext()) {
                systems.add(
                    QuizSystem(
                        id = it.getLong(0),
                        name = it.getString(1) ?: "",
                        count = if (it.isNull(2)) 0 else it.getInt(2)
                    )
                )
            }
        }
        
        systems
    }
}
