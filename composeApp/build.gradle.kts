plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // New Android-KMP plugin DSL (replaces androidTarget() + top-level android { })
    // https://developer.android.com/kotlin/multiplatform/plugin
    android {
        namespace = "com.medqb.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {
            // Enables host unit tests for Android target (androidUnitTest)
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        commonMain {
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

                // Room 3
                implementation(libs.room3.runtime)
                
                // Navigation 3
                implementation(libs.androidx.navigation3.ui)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)
                
                // Zoomable
                implementation(libs.zoomable)
            }
        }
        
        androidMain {
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
        
        getByName("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.cio)
                
                // VLC for video/audio playback
                implementation(libs.vlcj)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room3.compiler)
    add("kspDesktop", libs.room3.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

compose.desktop {
    application {
        mainClass = "com.medqb.app.shared.MainKt"
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
                menuGroup = "MedQB"
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
    }
}

