package com.medqb.app.shared.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.medqb.app.shared.data.local.entity.TextHighlightEntity

@Dao
interface RoomTextHighlightDao {

    @Query(
        """
        SELECT * FROM text_highlights 
        WHERE db_name = :dbName AND question_id = :questionId AND section = :section 
        ORDER BY start_offset
        """
    )
    suspend fun getBySection(dbName: String, questionId: Long, section: String): List<TextHighlightEntity>

    @Query(
        """
        SELECT * FROM text_highlights 
        WHERE db_name = :dbName AND question_id = :questionId 
        ORDER BY section, start_offset
        """
    )
    suspend fun getAllForQuestion(dbName: String, questionId: Long): List<TextHighlightEntity>

    @Insert
    suspend fun insert(highlight: TextHighlightEntity): Long

    @Query("DELETE FROM text_highlights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM text_highlights WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE text_highlights SET color = :color WHERE id = :id")
    suspend fun updateColor(id: Long, color: String)

    @Query(
        """
        DELETE FROM text_highlights 
        WHERE db_name = :dbName AND question_id = :questionId 
        AND (:section IS NULL OR section = :section)
        """
    )
    suspend fun clearForQuestion(dbName: String, questionId: Long, section: String? = null)

    @Query("DELETE FROM text_highlights WHERE db_name = :dbName")
    suspend fun clearForDatabase(dbName: String)

    @Transaction
    suspend fun replaceWithMerged(
        removeIds: List<Long>,
        insert: TextHighlightEntity
    ): Long {
        if (removeIds.isNotEmpty()) {
            deleteByIds(removeIds)
        }
        return insert(insert)
    }
}
