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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ========== Gson ==========
# 保留被 @SerializedName 标注的字段（Gson 反序列化需要）
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# 保留 Gson 数据类（用于 JSON 反序列化的 model）
-keep class com.ssjq.english.data.AppVersion { *; }
# 保留 TypeAdapter 及相关接口的实现
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# 保留 TypeToken（泛型类型擦除处理）
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ========== OkHttp ==========
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# 保留 OkHttp 平台检测相关类（HTTPS/TLS 需要）
-keep class okhttp3.internal.platform.** { *; }
-keep class okhttp3.internal.tls.** { *; }

# ========== Kotlin Coroutines ==========
# 协程已自带 consumer rules，通常无需手动添加
# 如遇反射相关问题可取消注释
#-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
