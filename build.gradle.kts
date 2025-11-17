// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Android Gradle Plugin updated to support compileSdk 35 for modern Compose libs
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
