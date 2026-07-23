package com.medqb.app.shared.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.medqb.app.shared.data.local.dao.RoomLogDao
import com.medqb.app.shared.data.local.dao.RoomTextHighlightDao
import com.medqb.app.shared.data.local.entity.LogEntity
import com.medqb.app.shared.data.local.entity.TextHighlightEntity

@Database(
    entities = [
        TextHighlightEntity::class,
        LogEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun textHighlightDao(): RoomTextHighlightDao
    abstract fun logDao(): RoomLogDao
}
