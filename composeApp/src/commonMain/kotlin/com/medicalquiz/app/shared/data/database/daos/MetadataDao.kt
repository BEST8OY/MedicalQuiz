package com.medicalquiz.app.shared.data.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.SQLiteQuery
import com.medicalquiz.app.shared.data.database.entities.SubjectEntity
import com.medicalquiz.app.shared.data.database.entities.SystemEntity

@Dao
interface MetadataDao {
    @Query("SELECT * FROM Subjects ORDER BY name")
    suspend fun getSubjects(): List<SubjectEntity>

    @Query("SELECT * FROM Systems ORDER BY name")
    suspend fun getSystems(): List<SystemEntity>

    @Query("SELECT * FROM Systems WHERE id IN (:ids) ORDER BY name")
    suspend fun getSystemsByIds(ids: List<Long>): List<SystemEntity>
    
    @RawQuery
    suspend fun getDistinctSystemIds(query: SQLiteQuery): List<String>
    
    @Query("SELECT name FROM Subjects WHERE id = :id")
    suspend fun getSubjectName(id: Long): String?

    @Query("SELECT name FROM Systems WHERE id = :id")
    suspend fun getSystemName(id: Long): String?
}
