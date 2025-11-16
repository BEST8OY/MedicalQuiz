plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.medicalquiz.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.medicalquiz.app"
        minSdk = 30
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    // Switched from LiveData to StateFlow/SharedFlow — use lifecycle runtime for repeatOnLifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // ViewPager2 for swipe navigation
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // SQLite support
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // Activity and Fragment KTX
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.0")
    
    // WebView for HTML rendering (questions contain HTML)
    implementation("androidx.webkit:webkit:1.10.0")

    // Media loading
    implementation("io.coil-kt:coil:2.6.0")
    // PhotoView for pinch-to-zoom in media viewer
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    
    // junit removed as part of deleting tests
        // core-testing removed per user request
        // Coroutines testing removed per user request
    // No instrumentation tests configured; these were removed to slim dependencies
}
