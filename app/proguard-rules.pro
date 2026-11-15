# Release builds are shrunk (see app/build.gradle.kts). These keep what must survive.

# The map engine calls into its Java side (and its GeoJSON classes) from native code,
# so none of it may be renamed or removed.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# The map library reads GeoJSON with Gson, which uses reflection.
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Keep line numbers so crash reports in Settings → Diagnostics point at the right code.
-keepattributes SourceFile,LineNumberTable

# Optional dependencies the map library references but doesn't need here.
-dontwarn com.google.android.gms.**
-dontwarn okhttp3.**
-dontwarn okio.**
