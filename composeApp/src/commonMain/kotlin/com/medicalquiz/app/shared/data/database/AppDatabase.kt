package com.medicalquiz.app.shared.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.medicalquiz.app.shared.data.database.daos.LogSummaryDao
import com.medicalquiz.app.shared.data.database.daos.MetadataDao
import com.medicalquiz.app.shared.data.database.daos.QuestionDao
import com.medicalquiz.app.shared.data.database.entities.*

@Database(
    entities = [
        QuestionEntity::class,
        AnswerEntity::class,
        SubjectEntity::class,
        SystemEntity::class,
        LogEntity::class
    ],
    views = [
        LogSummaryEntity::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun metadataDao(): MetadataDao
    abstract fun logSummaryDao(): LogSummaryDao
}
