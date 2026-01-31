# ProGuard rules for MedicalQuiz Compose Desktop
# Optimizes and shrinks the desktop application

# ==================== MAIN ENTRY POINT ====================

-keep class com.medicalquiz.app.shared.MainKt {
    public static void main(java.lang.String[]);
}

# ==================== GENERAL SETTINGS ====================

-keepattributes Signature,SourceFile,LineNumberTable,*Annotation*,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-renamesourcefileattribute SourceFile

# Optimization settings - be conservative to avoid bytecode issues
-optimizationpasses 3
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!method/inlining/*

# ==================== DONTWARN / DONTNOTE RULES ====================

# Suppress notes about duplicate classes and missing references
-dontnote **

# Kotlin
-dontwarn kotlin.**

# Kotlinx
-dontwarn kotlinx.datetime.**

# SLF4J (optional logging dependency)
-dontwarn org.slf4j.**

# Java instrumentation (used by coroutines debug agent)
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.SignalHandler
-dontwarn sun.misc.Signal
-dontwarn java.lang.ClassValue
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Java AWT/Swing (desktop UI)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn sun.awt.**
-dontwarn sun.java2d.**

# Okio
-dontwarn okio.**

# ==================== COMPOSE DESKTOP ====================

# Skiko rendering engine - required for Compose Desktop
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }
-dontwarn org.jetbrains.skiko.**
-dontwarn org.jetbrains.skia.**

# Compose itself can generally be shrunk safely; avoid blanket -keep or size won't improve.
# If you hit runtime VerifyError/ClassFormatError in release builds, we can selectively
# re-introduce targeted keep rules (or further restrict optimizations).
-dontwarn androidx.compose.**

# ==================== KOTLIN ====================

-keep class kotlin.Metadata { *; }

# ==================== KOTLINX COROUTINES ====================
# Official rules from: https://github.com/Kotlin/kotlinx.coroutines

# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Most volatile fields are updated with AFU and should not be mangled
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# SafeContinuation also uses AtomicReferenceFieldUpdater
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

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

# Keep annotations
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Don't print notes for kotlinx-serialization
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# Prevent optimization issues with descriptor field
-keepclassmembers public class **$$serializer {
    private ** descriptor;
    *** INSTANCE;
}

# ==================== KTOR ====================

# Ktor CIO engine for desktop
# Avoid keeping everything so the shrinker can remove unused plugins/features.
-dontwarn io.ktor.**

# ==================== COIL ====================

-dontwarn coil3.**

# ==================== OKIO ====================

-dontwarn okio.**

# ==================== KSOUP ====================

# HTML parsing library (uses reflection)
-keep class com.mohamedrejeb.ksoup.** { *; }
-dontwarn com.mohamedrejeb.ksoup.**

# ==================== VLCJ / JNA ====================

# VLCJ relies on JNA to load native libVLC and uses reflection/service loading in a few spots.
# With shrinking enabled, it's safer to keep these packages intact.
-keep class uk.co.caprica.vlcj.** { *; }
-dontwarn uk.co.caprica.vlcj.**

-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-keep class com.sun.jna.platform.** { *; }
-dontwarn com.sun.jna.platform.**

# ==================== SQLITE ====================
-dontwarn androidx.sqlite.**

# ==================== APP SPECIFIC ====================

# Keep data layer (serialization + reflection)
-keep class com.medicalquiz.app.shared.data.** { *; }

# Keep ViewModels (reflection-based instantiation)
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
