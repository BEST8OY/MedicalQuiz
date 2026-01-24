plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.medicalquiz.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.medicalquiz.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Split APKs by ABI for smaller size
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a") // include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false // Also build a universal APK
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)

    // Activity & Fragment KTX
    implementation(libs.androidx.activity.ktx)

    // ----- Jetpack Compose -----
    implementation(platform(libs.androidx.compose.bom))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.androidx.activity.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation(libs.coil.svg)
    implementation(libs.coil.android)

    implementation(project(":composeApp"))
}
