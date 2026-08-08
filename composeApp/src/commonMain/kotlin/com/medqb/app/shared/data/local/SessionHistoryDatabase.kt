package com.medqb.app.shared.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.medqb.app.shared.data.local.dao.RoomSessionHistoryDao
import com.medqb.app.shared.data.local.entity.QuizHistoryEntity
import com.medqb.app.shared.data.local.entity.QuizSessionEntity
import com.medqb.app.shared.data.local.entity.SessionLogLinkEntity

@Database(
    entities = [
        QuizSessionEntity::class,
        SessionLogLinkEntity::class,
        QuizHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@ConstructedBy(SessionHistoryDatabaseConstructor::class)
abstract class SessionHistoryDatabase : RoomDatabase() {
    abstract fun sessionHistoryDao(): RoomSessionHistoryDao
}

@Suppress("KotlinNoActualForExpect")
expect object SessionHistoryDatabaseConstructor : RoomDatabaseConstructor<SessionHistoryDatabase>
