package com.medqb.app.shared.data.database

data class QuizSessionHistoryRow(
    val sessionId: String,
    val databaseName: String,
    val entryName: String,
    val selectedSubjectIds: List<Long>,
    val selectedSystemIds: List<Long>,
    val performanceFilter: String,
    val currentQuestionIndex: Int,
    val updatedAt: Long,
    val isLoggingEnabled: Boolean,
    val submissionMode: String,
)
