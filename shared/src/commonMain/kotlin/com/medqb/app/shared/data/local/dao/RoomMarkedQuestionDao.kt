package com.medqb.app.shared.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.medqb.app.shared.data.local.entity.MarkedQuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomMarkedQuestionDao {

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM marked_questions 
            WHERE db_name = :dbName AND question_id = :questionId
        )
        """
    )
    suspend fun isMarked(dbName: String, questionId: Long): Boolean

    @Query(
        """
        SELECT question_id FROM marked_questions 
        WHERE db_name = :dbName 
        ORDER BY question_id
        """
    )
    suspend fun getMarkedQuestionIds(dbName: String): List<Long>

    @Query(
        """
        SELECT question_id FROM marked_questions 
        WHERE db_name = :dbName 
        ORDER BY question_id
        """
    )
    fun observeMarkedQuestionIds(dbName: String): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM marked_questions WHERE db_name = :dbName")
    suspend fun countMarked(dbName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MarkedQuestionEntity)

    @Query(
        """
        DELETE FROM marked_questions 
        WHERE db_name = :dbName AND question_id = :questionId
        """
    )
    suspend fun delete(dbName: String, questionId: Long)

    @Query("DELETE FROM marked_questions WHERE db_name = :dbName")
    suspend fun clearForDatabase(dbName: String)
}
