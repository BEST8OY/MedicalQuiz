package com.medicalquiz.app.shared.platform

import android.content.Context

object AppContext {
    private var _context: Context? = null
    val context: Context
        get() = _context ?: throw IllegalStateException("Context not initialized. Call AppContext.init(context) first.")

    fun init(context: Context) {
        _context = context.applicationContext
    }
}
