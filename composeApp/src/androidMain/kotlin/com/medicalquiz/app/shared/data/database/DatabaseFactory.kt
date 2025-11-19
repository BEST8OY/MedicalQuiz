package com.medicalquiz.app.shared.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

object AppContext {
    private var _context: Context? = null
    val context: Context
        get() = _context ?: throw IllegalStateException("Context not initialized. Call AppContext.init(context) first.")

    fun init(context: Context) {
        _context = context.applicationContext
    }
}

actual fun getDatabaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder(
        context = AppContext.context,
        name = path
    )
}
