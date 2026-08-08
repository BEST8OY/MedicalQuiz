package com.medqb.app.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "logs",
    indices = [Index(value = ["db_name", "qid"])]
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "db_name")
    val dbName: String,
    @ColumnInfo(name = "qid")
    val qid: Long,
    @ColumnInfo(name = "selected_answer")
    val selectedAnswer: Int,
    @ColumnInfo(name = "corr_answer")
    val corrAnswer: Int,
    @ColumnInfo(name = "time")
    val time: Long,
    @ColumnInfo(name = "answer_date")
    val answerDate: String
)
