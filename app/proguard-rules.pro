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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Jetpack Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keep class com.example.lddc.presentation.theme.** { *; }
-keep class com.example.lddc.ui.theme.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class com.example.lddc.di.** { *; }

# Room
-keep class com.example.lddc.data.local.database.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.Dao

# DataStore
-keep class com.example.lddc.data.local.datastore.** { *; }

# Ktor
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Serialization
-keep class kotlinx.serialization.** { *; }
-keep class com.example.lddc.common.models.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# Audio Tagging
-keep class org.jaudiotagger.** { *; }

# Coil
-keep class coil.** { *; }

# WorkManager
-keep class androidx.work.** { *; }

# Navigation
-keep class androidx.navigation.** { *; }

# Keep all model classes
-keep class com.example.lddc.common.models.** { *; }

# Keep all repository classes
-keep class com.example.lddc.data.repository.** { *; }

# Keep all use case classes
-keep class com.example.lddc.domain.** { *; }

# Keep all ViewModel classes
-keep class com.example.lddc.presentation.viewmodel.** { *; }
-keep class com.example.lddc.presentation.screens.**.*ViewModel { *; }

# Keep all components
-keep class com.example.lddc.presentation.components.** { *; }

# Keep all screens
-keep class com.example.lddc.presentation.screens.** { *; }

# Keep all utility classes
-keep class com.example.lddc.common.utils.** { *; }

# Keep all API classes
-keep class com.example.lddc.core.api.** { *; }

# Keep all decryptor classes
-keep class com.example.lddc.core.decryptor.** { *; }

# Keep all parser classes
-keep class com.example.lddc.core.parser.** { *; }

# Keep all enums
-keep enum com.example.lddc.common.models.enums.** { *; }

# Keep all annotation classes
-keepattributes *Annotation*

# Keep all kotlin metadata
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Keep all Kotlin classes
-keep class **$Companion { *; }
-keepclassmembers class **$Companion { *; }

# Keep all Kotlin data classes
-keep class * extends java.lang.Object { *; }

# Keep all Kotlin functions
-keepclassmembers class * {
    @kotlin.Metadata public *;
}

# Keep all AndroidX classes
-keep class androidx.** { *; }

# Keep all Google Play Services classes
-keep class com.google.** { *; }

# Keep all OkHttp classes
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep all Retrofit classes
-keep class retrofit2.** { *; }

# Keep all Gson classes
-keep class com.google.gson.** { *; }

# Keep all Jackson classes
-keep class com.fasterxml.jackson.** { *; }

# Keep all Log classes
-keep class android.util.Log { *; }

# Keep all BuildConfig classes
-keep class com.example.lddc.BuildConfig { *; }

# Keep all R classes
-keep class com.example.lddc.R { *; }

# Optimize code
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Print mapping file
-printmapping mapping.txt

# Print usage
-printusage usage.txt

# Don't warn about missing classes
-dontwarn **

# Don't note about missing classes
-dontnote **