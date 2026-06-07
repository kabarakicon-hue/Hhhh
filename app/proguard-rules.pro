# Advanced ProGuard/R8 Obfuscation & Hardening Configuration
# Designed to maximize security against reverse engineering and structural analysis

# 1. Structural Hardening & Name Obfuscating
-repackageclasses ""
-allowaccessmodification
-flogger:disabled

# Optimize rename dictionaries and inline functions aggressively
-overloadaggressively
-repackageclasses ''
-allowaccessmodification

# Hide original filenames and line numbers to prevent reverse engineering from trace diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute ""

# 2. Prevent Debug Logs Leakage in Release
# This completely strips out verbose and debug logs even if logs or bug-reporting is executed
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# 3. Component Keeps (Essential Android Manifest Classes)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Keep custom serializable objects and models intact
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room compiler models
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# Keep OkHttp, Coroutines, and Retrofit safe from aggressive shrinking failures
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**

# Keep Moshi JSON parsing code works natively
-keep class com.example.data.api.** { *; }
-keep class * { @com.squareup.moshi.Json *; }
