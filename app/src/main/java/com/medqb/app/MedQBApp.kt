package com.medqb.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class MedQBApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
