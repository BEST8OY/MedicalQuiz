package com.medicalquiz.app.shared.data.database

data class QuestionPerformance(
    val qid: Long,
    val lastCorrect: Boolean,
    val everCorrect: Boolean,
    val everIncorrect: Boolean,
    val attempts: Int
)
