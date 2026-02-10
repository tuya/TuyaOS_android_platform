# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep IoT SDK classes
-keep class com.tuya.smartai.iot_sdk.** { *; }
-dontwarn com.tuya.smartai.iot_sdk.**
