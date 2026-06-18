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

-keep class com.alibaba.fastjson.** { *; }
-dontwarn com.alibaba.fastjson.**

# fastjson DTO（相框 bean）：显式保留无参构造与字段，避免 R8 裁掉无参构造
# 导致反序列化报 "default constructor not found. class ...PhotoInfo"。
-keepclassmembers class com.thingclips.smart.ai.bs.bean.** {
    <init>();
    <fields>;
    void set*(***);
    *** get*();
}

-keep class com.thingclips.smart.mqttclient.mqttv3.** { *; }
-dontwarn com.thingclips.smart.mqttclient.mqttv3.**

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

-keep class okio.** { *; }
-dontwarn okio.**

-keep class com.thingclips.** { *; }
-dontwarn com.thingclips.**

-keep class chip.** { *; }
-dontwarn chip.**

-keep class com.gzl.smart.** { *; }
-dontwarn com.gzl.smart.**
