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
        minSdk = 31
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

    // Activity & Fragment KTX
    implementation("androidx.activity:activity-ktx:1.9.0")

    // ----- Jetpack Compose -----
    implementation(platform("androidx.compose:compose-bom:2025.11.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")

    implementation(project(":composeApp"))
}
