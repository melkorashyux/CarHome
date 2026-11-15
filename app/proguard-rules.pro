# Release builds are shrunk (see app/build.gradle.kts). These keep what must survive.

# The map engine calls into its Java side from native code.
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# Optional dependencies the map library references but doesn't need here.
-dontwarn com.google.android.gms.**
-dontwarn okhttp3.**
-dontwarn okio.**
