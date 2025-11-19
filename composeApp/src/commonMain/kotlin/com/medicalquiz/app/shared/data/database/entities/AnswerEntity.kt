package com.medicalquiz.app.shared.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Answers")
data class AnswerEntity(
    @PrimaryKey val answerId: Long,
    val answerText: String,
    val correctPercentage: Int?,
    val qId: Long
)
