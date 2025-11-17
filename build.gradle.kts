// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Android Gradle Plugin updated to support compileSdk 35 for modern Compose libs
    id("com.android.application") version "8.6.0" apply false
    // Kotlin Gradle plugin bumped to match Compose compiler requirements
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
}

tasks.register("clean", Delete::class) {
    // Use the layout API to avoid deprecated buildDir getter access
    delete(rootProject.layout.buildDirectory)
}
