# SDK 集成指南

## 1. Maven 仓库配置

```gradle
repositories {
    google()
    mavenCentral()
    maven { url 'https://maven-other.tuya.com/repository/maven-releases/' }
    maven { url 'https://maven-other.tuya.com/repository/maven-commercial-releases/' }
}
```

---

## 2. 组件总览

| 组件 | 说明 | 依赖关系 |
|------|------|---------|
| `thingiotsdk` | IoT 核心（必选） | 无 |
| `thingiotsdk-aistream` | AI 语音/视频流 | 依赖 thingiotsdk |
| `thingiotsdk-frame` | 相框业务 | 依赖 thingiotsdk |
| `thingiotsdk-license` | 在线授权烧录 | 独立模块，生产环节使用，为设备烧录一机一码 |

---

## 3. 依赖配置

```groovy
dependencies {

    def iot_version = "2.3.9.0" // 具体版本与对接开发确认

    // ==================== thingiotsdk（必选）====================
    implementation "com.thingclips.smart:thingiotsdk:$iot_version"
    implementation 'com.alibaba:fastjson:1.1.67.android'

    // ==================== thingiotsdk-aistream（可选）====================
    implementation "com.thingclips.smart:thingiotsdk-aistream:$iot_version"

    // ==================== thingiotsdk-frame（可选）====================
    implementation "com.thingclips.smart:thingiotsdk-frame:$iot_version"
    implementation 'com.qcloud.cos:cos-android:5.9.46'                          // 中国区
    implementation 'com.amazonaws:aws-android-sdk-core:2.22.1'                  // 海外
    implementation 'com.amazonaws:aws-android-sdk-s3:2.22.1'                    // 海外
    implementation 'com.microsoft.azure.android:azure-storage-android:2.0.0'    // 海外

    // ==================== thingiotsdk-license（可选）====================
    implementation "com.thingclips.smart:thingiotsdk-license:$iot_version"
    // Java 工程无需配置 kotlin-android 插件，添加以下运行时依赖即可
    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.25"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
    implementation 'androidx.appcompat:appcompat:1.3.1'
    implementation 'androidx.recyclerview:recyclerview:1.2.1'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.3.1'
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'
    implementation 'androidx.security:security-crypto:1.0.0'
}
```

---

## 4. 混淆规则

所有模块的混淆规则已内置在各自 AAR 中，通过 Maven 集成后无需额外配置。
