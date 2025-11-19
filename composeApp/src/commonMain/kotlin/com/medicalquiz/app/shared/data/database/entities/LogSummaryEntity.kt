package com.medicalquiz.app.shared.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs_summary")
data class LogSummaryEntity(
    @PrimaryKey val qid: Long,
    val lastCorrect: Int?,
    val everCorrect: Int?,
    val everIncorrect: Int?
)
