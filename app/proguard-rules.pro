# ProGuard rules for MedicalQuiz Android app
# https://developer.android.com/guide/developing/tools/proguard.html

# ==================== GENERAL SETTINGS ====================

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations needed for serialization and reflection
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod

# ==================== KOTLIN ====================

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Kotlin Coroutines - keep volatile fields for atomics
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Kotlin Serialization
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

# Kotlinx DateTime
-dontwarn kotlinx.datetime.**

# ==================== JETPACK COMPOSE ====================

# Keep Compose compiler generated classes
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ==================== COIL ====================

# Keep Coil image loader internals
-keep class coil3.** { *; }
-dontwarn coil3.**

# ==================== KTOR ====================

# Ktor client (OkHttp engine for Android)
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn io.ktor.**

# ==================== KSOUP ====================

# HTML parsing library
-keep class com.mohamedrejeb.ksoup.** { *; }
-dontwarn com.mohamedrejeb.ksoup.**

# ==================== SQLITE ====================

-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**

# ==================== APP SPECIFIC ====================

# Keep serializable data models
-keep class com.medicalquiz.app.shared.data.models.** { *; }
-keep class com.medicalquiz.app.shared.data.MediaDescription { *; }

# Keep ViewModels
-keep class com.medicalquiz.app.shared.viewmodel.** { *; }

# Keep main app entry points
-keep class com.medicalquiz.app.MainActivity { *; }
-keep class com.medicalquiz.app.MedicalQuizApp { *; }

# ==================== OPTIMIZATION ====================

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Remove Kotlin null checks in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
}
