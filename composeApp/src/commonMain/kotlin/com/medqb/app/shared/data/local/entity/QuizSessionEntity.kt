package com.medqb.app.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "quiz_sessions")
data class QuizSessionEntity(
    @PrimaryKey
    val sessionId: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String = ""
)
