package com.medqb.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.medqb.app.shared.di.AndroidAppGraph
import dev.zacsweers.metro.createGraph

class MedQBApp : Application() {
    lateinit var graph: AndroidAppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        graph = createGraph<AndroidAppGraph>()
    }
}
