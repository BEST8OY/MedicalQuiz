package com.medicalquiz.app.shared.data.database.entities

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "logs_summary",
    value = """
        SELECT 
            qid,
            (SELECT CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END FROM logs l2 WHERE l2.qid = logs.qid ORDER BY id DESC LIMIT 1) as lastCorrect,
            MAX(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as everCorrect,
            MAX(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as everIncorrect
        FROM logs
        GROUP BY qid
    """
)
data class LogSummaryEntity(
    val qid: Long,
    val lastCorrect: Int?,
    val everCorrect: Int?,
    val everIncorrect: Int?
)
