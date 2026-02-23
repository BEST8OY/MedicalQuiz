plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

kotlin {
    jvmToolchain(21)

    // New Android-KMP plugin DSL (replaces androidTarget() + top-level android { })
    // https://developer.android.com/kotlin/multiplatform/plugin
    android {
        namespace = "com.medicalquiz.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.materialIconsExtended)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.components.uiToolingPreview)
                
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.runtime.compose)
                
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)
                implementation(libs.coil.svg)
                
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                
                // Ksoup for HTML parsing
                implementation(libs.ksoup.html)

                implementation(libs.sqlite.bundled)
                
                // Navigation 3
                implementation(libs.androidx.navigation3.ui)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(libs.compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.navigationevent.compose)
                implementation(libs.coil.android)
                implementation(libs.ktor.client.okhttp)
                
                // Media3 for video/audio playback
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.ui)
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.cio)
                
                // VLC for video/audio playback
                implementation(libs.vlcj)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.medicalquiz.app.shared.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "MedicalQuiz"
            packageVersion = "1.0.0"
            
            // Reduce package size
            includeAllModules = false
            
            linux {
                val linuxIcon = project.file("src/desktopMain/resources/icon.png")
                if (linuxIcon.exists()) {
                    iconFile.set(linuxIcon)
                }
            }
            windows {
                val windowsIcon = project.file("src/desktopMain/resources/icon.ico")
                if (windowsIcon.exists()) {
                    iconFile.set(windowsIcon)
                }
                dirChooser = true
                menuGroup = "MedicalQuiz"
            }
            macOS {
                val macIcon = project.file("src/desktopMain/resources/icon.icns")
                if (macIcon.exists()) {
                    iconFile.set(macIcon)
                }
            }
        }
        
        // Enable ProGuard for release builds - significantly reduces size
        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(false) // Keep readable stack traces
            optimize.set(true)
            configurationFiles.from(project.file("proguard-desktop.pro"))
        }
        
        // Enable native Wayland support
        // Wayland uses a different rendering pipeline than X11 for better performance and security
        jvmArgs(
            "-Dawt.toolkit.name=WLToolkit",
            "-Dwayland.enabled=true",
            "-Djava.awt.headless=false"
        )
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
