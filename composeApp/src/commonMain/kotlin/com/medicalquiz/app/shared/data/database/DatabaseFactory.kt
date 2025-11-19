package com.medicalquiz.app.shared.data.database

import androidx.room.RoomDatabase

expect fun getDatabaseBuilder(path: String): RoomDatabase.Builder<AppDatabase>
