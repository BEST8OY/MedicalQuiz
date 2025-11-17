// Top-level build file
plugins {
    // AGP updated for Kotlin 2.2.0
    id("com.android.application") version "8.10.0" apply false
    // Kotlin updated to 2.2.0 to match dependencies
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    // This line adds the Compose Compiler plugin, which is needed for Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
}

tasks.register("clean", Delete::class.java) {
    delete(rootProject.layout.buildDirectory)
}