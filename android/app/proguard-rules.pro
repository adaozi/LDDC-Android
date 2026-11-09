# jaudiotagger / TagLib JNI 桥在 release 下需要保留
-keep class org.lddc.android.data.tag.TagLibBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# kotlinx.serialization（@Serializable 类的伴生 serializer）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep @kotlinx.serialization.Serializable class * { *; }

# 设置类被 DataStore/序列化反射读取
-keep class org.lddc.android.data.settings.** { *; }

# OkHttp/Okio 的可选依赖
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
