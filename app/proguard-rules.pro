# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers so Crashlytics can map obfuscated release stack traces
# back to source lines (the Crashlytics Gradle plugin uploads the mapping file
# automatically). SourceFile is renamed to a placeholder to avoid leaking the
# original .kt file names while keeping the line table intact.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip all android.util.Log calls from release builds. R8 treats these methods
# as side-effect free, so calls whose return value is unused are removed along
# with the string-building that fed them — this keeps location/PII out of the
# device logcat in production.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Retrofit/Gson integration DTOs use field names that must remain stable for
# provider JSON wire formats.
-keep class com.example.mileagetracker.data.remote.integrations.** { *; }

# Keep domain models serialized into encrypted preferences/report payloads.
-keep class com.example.mileagetracker.domain.model.IntegrationConfig { *; }
-keep class com.example.mileagetracker.domain.model.IntegrationProvider { *; }
