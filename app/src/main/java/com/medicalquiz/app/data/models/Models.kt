package com.medicalquiz.app.data.models

data class Question(
    val id: Long,
    val question: String,
    val explanation: String,
    val corrAns: Int,  // Correct answer ID
    val title: String?,
    val mediaName: String?,
    val otherMedias: String?,
    val pplTaken: Double?,
    val corrTaken: Double?,
    val subId: String?,  // Comma-separated subject IDs
    val sysId: String?,  // Comma-separated system IDs
    var subName: String? = null,  // Resolved subject names
    var sysName: String? = null   // Resolved system names
)

data class Answer(
    val answerId: Long,
    val answerText: String,
    val correctPercentage: Int?,
    val qId: Long
)

data class Subject(
    val id: Long,
    val name: String,
    val count: Int
)

data class System(
    val id: Long,
    val name: String,
    val count: Int
)

data class LogEntry(
    val id: Long = 0,
    val qid: Long,
    val selectedAnswer: Int,
    val corrAnswer: Int,
    val time: Long,
    val answerDate: String,
    val testId: String
)

data class QuestionStats(
    val attempts: Int = 0,
    val correct: Int = 0,
    val incorrect: Int = 0
)

enum class QuestionStatus {
    UNANSWERED,
    CORRECT,
    INCORRECT
}
