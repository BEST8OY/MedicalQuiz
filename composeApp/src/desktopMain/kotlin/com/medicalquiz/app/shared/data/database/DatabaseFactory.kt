package com.medicalquiz.app.shared.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual fun getDatabaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder(name = path)
}
