plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.matching { it.name == "run" }.configureEach {
    (this as? JavaExec)?.workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.ui)
    implementation(libs.compose.runtime)
    implementation(libs.compose.components.resources)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.medqb.app.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "MedQB"
            packageVersion = "1.0.0"
            
            // Reduce package size
            includeAllModules = false
            
            linux {
                val linuxIcon = project.file("src/main/resources/icon.png")
                if (linuxIcon.exists()) {
                    iconFile.set(linuxIcon)
                }
            }
            windows {
                val windowsIcon = project.file("src/main/resources/icon.ico")
                if (windowsIcon.exists()) {
                    iconFile.set(windowsIcon)
                }
                dirChooser = true
                menuGroup = "MedQB"
            }
            macOS {
                val macIcon = project.file("src/main/resources/icon.icns")
                if (macIcon.exists()) {
                    iconFile.set(macIcon)
                }
            }
        }
        
        // Enable ProGuard for release builds - significantly reduces size
        buildTypes.release.proguard {
            version.set("7.10.0")
            isEnabled.set(true)
            obfuscate.set(false) // Keep readable stack traces
            optimize.set(true)
            configurationFiles.from(project.file("proguard-desktop.pro"))
        }
    }
}
