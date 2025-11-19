package com.medicalquiz.app.shared.data.models

data class Answer(
    val answerId: Long,
    val answerText: String,
    val correctPercentage: Int?,
    val qId: Long
)
