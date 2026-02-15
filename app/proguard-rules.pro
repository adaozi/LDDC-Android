# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, InnerClasses, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Ktor - 只保留必要的类
-keep class io.ktor.client.** { *; }
-keepclassmembers class io.ktor.client.** { *; }
-dontwarn io.ktor.util.debug.**

# Compose - 只保留必要的类
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keepclassmembers class androidx.compose.ui.** { *; }

# 保留模型类
-keep class com.example.lddc.model.** { *; }
-keepclassmembers class com.example.lddc.model.** { *; }

# 保留网络相关类
-keep class com.example.lddc.service.api.** { *; }
-keepclassmembers class com.example.lddc.service.api.** { *; }

# 移除日志代码
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 忽略缺失的类（这些类在Android上不可用）
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder

# 优化
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# 压缩
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
