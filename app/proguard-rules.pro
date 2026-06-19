# ProGuard rules for MedQB Android app
# https://developer.android.com/guide/developing/tools/proguard.html

# ==================== GENERAL SETTINGS ====================

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations needed for serialization and reflection
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

# ==================== KOTLIN ====================

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ==================== KOTLINX COROUTINES ====================
# Coroutines v1.7.0+ bundles its own consumer rules — no manual rules needed.

# Debug agent classes (not needed in release)
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.SignalHandler
-dontwarn sun.misc.Signal
-dontwarn java.lang.ClassValue
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlinx.coroutines.**

# ==================== KOTLINX SERIALIZATION ====================
# Official rules from: https://github.com/Kotlin/kotlinx.serialization

# Keep Companion object fields of serializable classes
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$* Companion;
}

# Keep named companion objects
-keepnames @kotlinx.serialization.internal.NamedCompanion class *
-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembernames class * {
    static <1> *;
}

# Keep serializer() on companion objects
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep INSTANCE.serializer() of serializable objects
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Don't print notes for kotlinx-serialization
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# Prevent optimization issues with descriptor field
-keepclassmembers public class **$$serializer {
    private ** descriptor;
    *** INSTANCE;
}

# Kotlinx DateTime
-dontwarn kotlinx.datetime.**

# ==================== JETPACK COMPOSE ====================

# Compose/Coil/Ktor/Okio/Ksoup/SQLite/Media3
# Intentionally no broad -keep rules here.
# These libraries provide consumer ProGuard rules and/or do not rely on reflection.
# Keeping them all would significantly reduce R8 shrinking effectiveness.

# ==================== KSOUP ====================

# HTML parsing library — keep only the public API classes that may use reflection
-keep class com.mohamedrejeb.ksoup.html.Ksoup { *; }
-keep class com.mohamedrejeb.ksoup.html.KsoupConverter { *; }
-dontwarn com.mohamedrejeb.ksoup.**

# ==================== APP SPECIFIC ====================

# Keep serializable data models (needed for kotlinx.serialization)
-keepclassmembers @kotlinx.serialization.Serializable class com.medqb.app.shared.data.models.** {
    <fields>;
    <init>(...);
}

# Keep main app entry points (Android manifest references)
-keep class com.medqb.app.MainActivity { *; }
-keep class com.medqb.app.MedQBApp { *; }

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
