plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.medicalquiz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.medicalquiz.app"
        minSdk = 30
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        // ViewBinding removed — using Jetpack Compose for UI surfaces
        viewBinding = false
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    // Upgrade core for modern APIs and compileSdk 35
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    // Switched from LiveData to StateFlow/SharedFlow — use lifecycle runtime for repeatOnLifecycle
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
    // Pager for swipe/carousel UI in Compose (official pager from Compose Foundation)
    implementation("androidx.compose.foundation:foundation-layout")

    // Media loading (Coil 3) - updated for modern Compose compatibility
    implementation("io.coil-kt.coil3:coil:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")


    // Note: `ui-viewbinding` removed since the project uses Compose exclusively for UI
    // Optional: Accompanist Pager removed — using official Compose pager
    
    // junit removed as part of deleting tests
        // core-testing removed per user request
        // Coroutines testing removed per user request
    // No instrumentation tests configured; these were removed to slim dependencies
}
