package com.medqb.app.shared.data.models

data class Answer(
    val answerId: Long,
    val answerText: String,
    val correctPercentage: Int?,
    val qId: Long
)
