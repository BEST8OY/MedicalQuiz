plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // 1. ADD the Compose Compiler plugin
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.medicalquiz.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.medicalquiz.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // No Android instrumentation tests in this project, runner removed
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // 2. UPDATE Java compatibility to 17 (recommended for modern AGP/Kotlin)
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // 3. UPDATE JVM target to 17
        jvmTarget = "17"
    }

    buildFeatures {
        // ViewBinding removed — using Jetpack Compose for UI surfaces
        viewBinding = false
        compose = true
    }
}

dependencies {
    // Your dependencies are very new (alpha/beta), which is why they require Kotlin 2.2.0
    // We will leave them as-is, but update Coil to match the version from your error log.
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    
    // SQLite support
    implementation("androidx.sqlite:sqlite-ktx:2.6.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    
    // Activity and Fragment KTX
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.0")
    
    // WebView for HTML rendering (questions contain HTML)
    implementation("androidx.webkit:webkit:1.10.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2025.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.foundation:foundation-layout")

    implementation("io.coil-kt.coil3:coil:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
}