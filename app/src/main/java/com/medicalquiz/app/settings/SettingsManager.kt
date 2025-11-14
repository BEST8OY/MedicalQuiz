package com.medicalquiz.app.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isLoggingEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOGGING_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LOGGING_ENABLED, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "medical_quiz_settings"
        private const val KEY_LOGGING_ENABLED = "log_answers_enabled"
    }
}
