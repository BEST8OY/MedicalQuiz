package com.medicalquiz.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class MedicalQuizApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
