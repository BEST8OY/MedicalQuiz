// Top-level build file
plugins {
    // AGP updated for Kotlin 2.2.0
    id("com.android.application") version "8.10.0" apply false
    // Kotlin updated to 2.0.20 to match KSP
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.0.20" apply false
    // This line adds the Compose Compiler plugin, which is needed for Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.24" apply false
}

tasks.register("clean", Delete::class.java) {
    delete(rootProject.layout.buildDirectory)
}