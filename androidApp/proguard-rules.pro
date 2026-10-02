# ProGuard rules for MedQB Android app
# https://developer.android.com/guide/developing/tools/proguard.html

# ==================== GENERAL SETTINGS ====================

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations needed for serialization and reflection
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

# ==================== KOTLIN ====================

# Note: R8 automatically handles kotlin.Metadata; manual keep is omitted.
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
# kotlinx-serialization-core v1.3.0+ bundles its own consumer rules.
# Suppress benign notes/warnings for internal classes:
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

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

# Keep serializable data models (needed for kotlinx.serialization reflection/descriptor access)
-keepclassmembers @kotlinx.serialization.Serializable class com.medqb.app.shared.data.models.** {
    <fields>;
    <init>(...);
}

# Entry points: MainActivity and MedQBApp are declared in AndroidManifest.xml;
# AAPT2 generates the necessary keep rules automatically.

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
