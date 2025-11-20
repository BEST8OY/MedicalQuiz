package com.medicalquiz.app.shared.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val qid: Long,
    val selectedAnswer: Int,
    val corrAnswer: Int,
    val time: Long,
    val answerDate: String,
    val testId: Long?
)
