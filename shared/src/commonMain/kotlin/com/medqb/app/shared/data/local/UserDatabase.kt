package com.medqb.app.shared.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.local.dao.RoomMarkedQuestionDao
import com.medqb.app.shared.data.local.dao.RoomSessionHistoryDao
import com.medqb.app.shared.data.local.dao.RoomTextHighlightDao
import com.medqb.app.shared.data.local.entity.LogEntity
import com.medqb.app.shared.data.local.entity.MarkedQuestionEntity
import com.medqb.app.shared.data.local.entity.QuizHistoryEntity
import com.medqb.app.shared.data.local.entity.QuizSessionEntity
import com.medqb.app.shared.data.local.entity.SessionLogLinkEntity
import com.medqb.app.shared.data.local.entity.TextHighlightEntity

/**
 * Single user-facing database: answer logs, text highlights, quiz sessions/history, marked questions.
 *
 * Merged from the former user_data.db + session_history.db so multi-step writes
 * spanning logs and session links commit atomically in one transaction.
 */
@Database(
    entities = [
        TextHighlightEntity::class,
        LogEntity::class,
        QuizSessionEntity::class,
        SessionLogLinkEntity::class,
        QuizHistoryEntity::class,
        MarkedQuestionEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@ConstructedBy(UserDatabaseConstructor::class)
abstract class UserDatabase : RoomDatabase() {
    abstract fun textHighlightDao(): RoomTextHighlightDao
    abstract fun logDao(): RoomLogDao
    abstract fun sessionHistoryDao(): RoomSessionHistoryDao
    abstract fun markedQuestionDao(): RoomMarkedQuestionDao
}

@Suppress("KotlinNoActualForExpect")
expect object UserDatabaseConstructor : RoomDatabaseConstructor<UserDatabase>
