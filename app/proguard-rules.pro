# Ran ProGuard rules.

# Keep Room entities and generated implementations.
-keep class com.ran.app.data.local.entity.** { *; }

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile