# ProGuard rules for MedicalQuiz Compose Desktop
# Optimizes and shrinks the desktop application

# ==================== MAIN ENTRY POINT ====================

-keep class com.medicalquiz.app.shared.MainKt {
    public static void main(java.lang.String[]);
}

# ==================== GENERAL SETTINGS ====================

-keepattributes Signature,SourceFile,LineNumberTable,*Annotation*,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Optimization
-optimizationpasses 5
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ==================== DONTWARN RULES ====================

# Kotlin
-dontwarn kotlin.**
-dontwarn kotlin.jvm.internal.**

# Kotlinx
-dontwarn kotlinx.datetime.**
-dontwarn kotlinx.coroutines.**

# SLF4J (optional logging dependency)
-dontwarn org.slf4j.**

# Java AWT/Swing (desktop UI)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn sun.awt.**
-dontwarn sun.java2d.**

# ==================== COMPOSE DESKTOP ====================

# Skiko rendering engine - required for Compose Desktop
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }
-dontwarn org.jetbrains.skiko.**
-dontwarn org.jetbrains.skia.**

# Compose runtime
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ==================== KOTLIN ====================

-keep class kotlin.Metadata { *; }

# Coroutines - keep volatile fields for atomics
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

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

# ==================== KTOR ====================

# Ktor CIO engine for desktop
-keep class io.ktor.client.engine.cio.** { *; }
-dontwarn io.ktor.**

# ==================== COIL ====================

-keep class coil3.** { *; }
-dontwarn coil3.**

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

# ==================== OPTIMIZATION ====================

# Remove Kotlin null checks in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
}

# Remove println logging
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
}
