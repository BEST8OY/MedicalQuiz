# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ==================== GENERAL SETTINGS ====================

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ==================== KOTLIN ====================

-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.**
-dontwarn kotlin.jvm.internal.**

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

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
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.datetime.**

# ==================== JETPACK COMPOSE ====================

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }

# ==================== COIL ====================

-keep class coil.** { *; }
-dontwarn coil.**

# ==================== KTOR ====================

-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ==================== SQLITE ====================

-keep class androidx.sqlite.** { *; }
-keep class app.cash.sqldelight.** { *; }

# ==================== APP SPECIFIC ====================

# Keep your data classes
-keep class com.medicalquiz.app.shared.data.** { *; }
-keep class com.medicalquiz.app.shared.data.models.** { *; }

# Keep UI state classes
-keep class com.medicalquiz.app.shared.ui.** { *; }

# Keep ViewModels
-keep class com.medicalquiz.app.shared.viewmodel.** { *; }

# Keep main app classes
-keep class com.medicalquiz.app.MainActivity { *; }
-keep class com.medicalquiz.app.MedicalQuizApp { *; }

# ==================== OPTIMIZATION ====================

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
}
