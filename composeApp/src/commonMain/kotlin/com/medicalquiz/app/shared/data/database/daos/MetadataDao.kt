package com.medicalquiz.app.shared.data.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
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
    suspend fun getDistinctSystemIds(query: RoomRawQuery): List<String>
    
    @Query("SELECT name FROM Subjects WHERE id = :id")
    suspend fun getSubjectName(id: Long): String?

    @Query("SELECT name FROM Systems WHERE id = :id")
    suspend fun getSystemName(id: Long): String?

    @Query("SELECT DISTINCT sysId FROM SubjectsSystems WHERE subId IN (:subjectIds)")
    suspend fun getSystemIdsForSubjects(subjectIds: List<Long>): List<Long>

    @Query("SELECT type FROM pragma_table_info('Questions') WHERE name = :columnName")
    suspend fun getColumnType(columnName: String): String?
}
