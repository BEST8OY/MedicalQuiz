plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    androidTarget()
    
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
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(libs.compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.coil.android)
                implementation(libs.ktor.client.okhttp)
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

android {
    namespace = "com.medicalquiz.app.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
                dirChooser = true
                menuGroup = "MedicalQuiz"
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
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
