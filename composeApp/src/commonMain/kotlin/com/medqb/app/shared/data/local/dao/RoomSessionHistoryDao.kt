package com.medqb.app.shared.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.medqb.app.shared.data.local.entity.QuizHistoryEntity
import com.medqb.app.shared.data.local.entity.QuizSessionEntity
import com.medqb.app.shared.data.local.entity.SessionLogLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomSessionHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureSessionExists(session: QuizSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLogLink(link: SessionLogLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: QuizHistoryEntity)

    @Query("SELECT * FROM quiz_history ORDER BY updated_at DESC")
    fun listHistory(): Flow<List<QuizHistoryEntity>>

    @Query("SELECT * FROM quiz_history ORDER BY updated_at DESC")
    suspend fun listHistoryOnce(): List<QuizHistoryEntity>

    @Query("SELECT * FROM quiz_history WHERE session_id = :sessionId")
    suspend fun getHistory(sessionId: String): QuizHistoryEntity?

    @Query("DELETE FROM quiz_history WHERE session_id IN (:sessionIds)")
    suspend fun deleteHistory(sessionIds: List<String>)

    @Query("UPDATE quiz_history SET entry_name = :name WHERE session_id = :sessionId")
    suspend fun renameHistory(sessionId: String, name: String)

    @Query("DELETE FROM session_log_links WHERE log_rowid IN (:logRowids)")
    suspend fun cleanupLinksForLogs(logRowids: List<Long>)

    @Query(
        """
        DELETE FROM quiz_sessions 
        WHERE session_id NOT IN (SELECT session_id FROM quiz_history)
        AND session_id NOT IN (SELECT DISTINCT session_id FROM session_log_links)
        """
    )
    suspend fun deleteOrphanedSessions()
}
