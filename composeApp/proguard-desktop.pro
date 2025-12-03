# ProGuard rules for Compose Desktop
# Optimizes and shrinks the desktop application

# Keep the main entry point
-keep class com.medicalquiz.app.shared.MainKt {
    public static void main(java.lang.String[]);
}

# ==================== DONTWARN RULES ====================
# Suppress warnings for missing classes that are not used at runtime

# Kotlin internal classes
-dontwarn kotlin.jvm.internal.EnhancedNullability
-dontwarn kotlin.concurrent.atomics.**

# Kotlinx libraries
-dontwarn kotlinx.datetime.**
-dontwarn kotlinx.io.**
-dontwarn kotlinx.coroutines.**

# SLF4J logging (optional dependency)
-dontwarn org.slf4j.**

# Compose Desktop rules
-dontwarn org.jetbrains.skiko.**
-keep class org.jetbrains.skiko.** { *; }
-keep class androidx.compose.** { *; }

# ==================== KEEP RULES ====================

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
-keep class kotlinx.datetime.Clock { *; }
-keep class kotlinx.datetime.Clock$System { *; }
-keep class kotlinx.datetime.Instant { *; }
-keep class kotlinx.datetime.TimeZone { *; }
-keep class kotlinx.datetime.TimeZoneKt { *; }
-keep class kotlinx.datetime.LocalDateTime { *; }

# Kotlinx IO
-keep class kotlinx.io.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.** { *; }

# SQLite
-keep class app.cash.sqldelight.** { *; }
-keep class androidx.sqlite.** { *; }

# Keep data classes used for serialization
-keep class com.medicalquiz.app.shared.data.** { *; }
-keep class com.medicalquiz.app.shared.data.models.** { *; }

# Keep UI state classes
-keep class com.medicalquiz.app.shared.ui.** { *; }

# Keep ViewModels
-keep class com.medicalquiz.app.shared.viewmodel.** { *; }

# Kotlin reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }

# General rules
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-renamesourcefileattribute SourceFile

# Optimization settings
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Remove logging in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
}
