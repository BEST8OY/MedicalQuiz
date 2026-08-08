package com.medqb.app.shared.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.medqb.app.shared.data.local.entity.LogEntity

data class QuestionPerformanceResult(
    val lastCorrect: Long,
    val everCorrect: Long,
    val everIncorrect: Long,
    val attempts: Long,
    val correctCount: Long,
    val incorrectCount: Long,
)

@Dao
interface RoomLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: LogEntity): Long

    @Query("DELETE FROM logs WHERE db_name = :dbName AND qid = :qid")
    suspend fun clearForQuestion(dbName: String, qid: Long)

    @Query("SELECT id FROM logs WHERE db_name = :dbName AND qid = :qid")
    suspend fun getLogRowIds(dbName: String, qid: Long): List<Long>

    @Query(
        """
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
                (CASE WHEN selected_answer = corr_answer THEN 1 ELSE 0 END) as lastCorrect
            FROM logs
            WHERE db_name = :dbName AND qid = :qid
            ORDER BY id DESC
            LIMIT 1
        ) latest
        JOIN (
            SELECT
                qid,
                MAX(CASE WHEN selected_answer = corr_answer THEN 1 ELSE 0 END) as everCorrect,
                MAX(CASE WHEN selected_answer != corr_answer THEN 1 ELSE 0 END) as everIncorrect,
                COUNT(*) as attempts,
                SUM(CASE WHEN selected_answer = corr_answer THEN 1 ELSE 0 END) as correctCount,
                SUM(CASE WHEN selected_answer != corr_answer THEN 1 ELSE 0 END) as incorrectCount
            FROM logs
            WHERE db_name = :dbName AND qid = :qid
            GROUP BY qid
        ) agg ON agg.qid = latest.qid
        """
    )
    suspend fun getQuestionPerformance(dbName: String, qid: Long): QuestionPerformanceResult?

    @Query(
        """
        SELECT qid FROM logs
        WHERE db_name = :dbName
        GROUP BY qid
        """
    )
    suspend fun getAllLoggedQids(dbName: String): List<Long>
}
