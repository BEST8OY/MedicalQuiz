package com.medqb.app.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "marked_questions",
    primaryKeys = ["db_name", "question_id"],
)
data class MarkedQuestionEntity(
    @ColumnInfo(name = "db_name")
    val dbName: String,
    @ColumnInfo(name = "question_id")
    val questionId: Long,
    @ColumnInfo(name = "marked_at")
    val markedAt: Long,
)
