// Top-level build file
plugins {
    // AGP updated for Kotlin 2.2.0
    id("com.android.application") version "8.10.0" apply false
    // Kotlin updated to 2.2.0 to match dependencies
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    // ADD THIS LINE for the Compose Compiler plugin
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

After making changes to **both** files, sync Gradle again, and your build should be able to resolve all the Kotlin version conflicts.