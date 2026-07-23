package com.medqb.app.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "quiz_history")
data class QuizHistoryEntity(
    @PrimaryKey
    val sessionId: String,
    @ColumnInfo(name = "database_name")
    val databaseName: String,
    @ColumnInfo(name = "entry_name")
    val entryName: String = "",
    @ColumnInfo(name = "selected_subject_ids")
    val selectedSubjectIds: String = "[]",
    @ColumnInfo(name = "selected_system_ids")
    val selectedSystemIds: String = "[]",
    @ColumnInfo(name = "performance_filter")
    val performanceFilter: String = "ALL",
    @ColumnInfo(name = "current_question_index")
    val currentQuestionIndex: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0,
    @ColumnInfo(name = "is_logging_enabled")
    val isLoggingEnabled: Boolean = false,
    @ColumnInfo(name = "submission_mode")
    val submissionMode: String = "INSTANT"
)
