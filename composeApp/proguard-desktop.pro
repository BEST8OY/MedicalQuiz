# ProGuard rules for Compose Desktop
# Optimizes and shrinks the desktop application

# Keep the main entry point
-keep class com.medicalquiz.app.shared.MainKt {
    public static void main(java.lang.String[]);
}

# Compose Desktop rules
-dontwarn org.jetbrains.skiko.**
-keep class org.jetbrains.skiko.** { *; }
-keep class androidx.compose.** { *; }

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
-dontwarn kotlinx.coroutines.**

# SQLite
-keep class app.cash.sqldelight.** { *; }
-keep class androidx.sqlite.** { *; }

# Keep data classes used for serialization
-keep class com.medicalquiz.app.shared.data.** { *; }
-keep class com.medicalquiz.app.shared.data.models.** { *; }

# Keep ViewModels
-keep class com.medicalquiz.app.shared.viewmodel.** { *; }

# Kotlin reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# General Android-like rules
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
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
