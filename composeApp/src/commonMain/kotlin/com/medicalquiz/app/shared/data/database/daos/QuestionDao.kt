package com.medicalquiz.app.shared.data.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import com.medicalquiz.app.shared.data.database.entities.AnswerEntity
import com.medicalquiz.app.shared.data.database.entities.QuestionEntity

@Dao
interface QuestionDao {
    @RawQuery
    suspend fun getQuestionIds(query: RoomRawQuery): List<Long>

    @Query("SELECT * FROM Questions WHERE id = :id")
    suspend fun getQuestionById(id: Long): QuestionEntity?

    @Query("SELECT * FROM Answers WHERE qId = :questionId ORDER BY answerId")
    suspend fun getAnswersForQuestion(questionId: Long): List<AnswerEntity>
}
