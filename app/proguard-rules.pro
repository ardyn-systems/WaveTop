# WaveTop release shrinking rules.

# osmdroid references optional pieces it doesn't need at runtime here.
-dontwarn org.osmdroid.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.**

# Keep line numbers so crash reports from release builds are readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
