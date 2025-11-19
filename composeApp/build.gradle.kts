plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
    id("com.google.devtools.ksp")
}

kotlin {
    androidTarget()
    
    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
                
                implementation("io.coil-kt.coil3:coil-compose:3.0.0-rc02")
                implementation("io.coil-kt.coil3:coil-network-ktor:3.0.0-rc02")
                
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                
                // Ksoup for HTML parsing
                implementation("com.mohamedrejeb.ksoup:ksoup-html:0.3.0")

                implementation("androidx.room:room-runtime:2.7.0-alpha01")
                implementation("androidx.sqlite:sqlite-bundled:2.5.0-alpha01")
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("io.coil-kt.coil3:coil-android:3.0.0-rc02")
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
            }
        }
    }
}

android {
    namespace = "com.medicalquiz.app.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.7.0-alpha01")
    add("kspDesktop", "androidx.room:room-compiler:2.7.0-alpha01")
}
