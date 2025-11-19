package com.medicalquiz.app.shared.data.database.daos

import androidx.room.Dao
import androidx.room.Query
import com.medicalquiz.app.shared.data.database.entities.LogSummaryEntity

@Dao
interface LogSummaryDao {
    @Query("SELECT * FROM logs_summary WHERE qid = :qid")
    suspend fun getSummaryForQuestion(qid: Long): LogSummaryEntity?
    
    @Query("DELETE FROM logs_summary")
    suspend fun clearLogs()
}
