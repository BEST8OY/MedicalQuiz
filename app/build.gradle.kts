plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // Compose compiler plugin (Kotlin 2.2)
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

    // ----- Java Version -----
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ----- Kotlin Compiler Options (NEW DSL – replaces kotlinOptions) -----
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)

            // (Optional but recommended for Kotlin 2.2)
            freeCompilerArgs.addAll(
                "-Xcontext-parameters",
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Compose Compiler now auto-matches Kotlin 2.2 because of the plugin
        // No need to specify kotlinCompilerExtensionVersion
    }
}

dependencies {

    // AndroidX base libraries
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")

    // SQLite
    implementation("androidx.sqlite:sqlite-ktx:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Activity & Fragment KTX
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.0")

    // WebView (HTML in questions)
    implementation("androidx.webkit:webkit:1.10.0")

    // ----- Jetpack Compose -----
    implementation(platform("androidx.compose:compose-bom:2025.11.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Coil 3 (Kotlin 2+ compatible)
    implementation("io.coil-kt.coil3:coil:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
}
