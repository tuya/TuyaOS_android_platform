# Android 设备端 SDK 双向 IPC 接入

适用设备 SDK：`com.thingclips.smart:thingiotsdk:2.3.5-ipc`。

本文面向 Android 设备开发者，说明如何在原有 IPC 上行预览基础上，接收并播放手机 App 发送的音视频，实现双向 IPC。设备侧的接入流程为：升级 SDK、初始化并注册监听、接收编码帧、解码播放，以及结束时释放资源。

| 接入内容 | 设备 SDK 接口 | 设备应用处理 |
|---|---|---|
| 接收手机视频 | `IPCEventListener.onVideoFrame` | 将 H.264 解码并显示到设备屏幕。 |
| 接收手机音频 | `IPCEventListener.onAudioFrame` | 将 G711U 解码为 PCM16，并通过设备扬声器或耳机播放。 |
| 保持原有设备上行 | `appendVideoFrame`、`appendAudioFrame` | 沿用设备摄像头、麦克风采集和编码流程。 |

SDK 提供音视频传输和编码帧回调，设备应用负责显示与播放。App 侧配合流程和呼叫应答业务见文末附录。

## 1. 接入前提

- 设备已完成联网、激活和绑定，原有视频预览及声音正常。基础接入继续参考[SDK 集成指南](https://github.com/tuya/TuyaOS_android_platform/blob/main-ipc/doc/SDK%E9%9B%86%E6%88%90%E6%8C%87%E5%8D%97.md)和[基础 IoT 文档](https://github.com/tuya/TuyaOS_android_platform/blob/main-ipc/doc/SDK%E4%BD%BF%E7%94%A8%E6%96%87%E6%A1%A3%201-%E5%9F%BA%E7%A1%80IoT%E6%96%87%E6%A1%A3.md)，已有产品授权可继续使用。
- 设备应用具备视频显示视图、H.264 解码和 G711U 音频播放能力；接入方式见第 3.2 节。
- 联调 App 支持目标设备的双向视频能力，已获摄像头、麦克风权限，并能启动音视频发送，配合流程见附录 A。
- 采用 App 主动呼叫设备的业务时，设备需处理接听、拒接和挂断；已有应答模块可继续复用，首次接入要求见附录 B。

## 2. 设备端 SDK 集成

设备最低支持 Android API 24，支持 `arm64-v8a` 和 `armeabi-v7a`。

```groovy
repositories {
    google()
    mavenCentral()
    maven { url 'https://maven-other.tuya.com/repository/maven-commercial-releases/' }
}

dependencies {
    implementation 'com.thingclips.smart:thingiotsdk:2.3.5-ipc'
    implementation 'com.alibaba:fastjson:1.1.67.android'
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.20'
}
```

保留 Kotlin 和 FastJSON 运行时依赖，已有兼容版本可统一管理。检查最终解析的设备 SDK 为 `2.3.5-ipc`，避免旧 AAR 或 `jniLibs` 覆盖新版。

AAR 已包含 consumer ProGuard 规则，以及两种 ABI 的 `libtuya_iot_android.so` 和 `libc++_shared.so`。按 Maven 集成时无需另外复制这些库；确保最终 APK 包含目标 ABI，其他 SDK 带入同名库时先确认兼容性。

设备采集摄像头和麦克风时，也需声明并申请 `CAMERA`、`RECORD_AUDIO` 权限；网络和基础 IoT 配置沿用原工程。

## 3. 设备端初始化与接收

### 3.1 初始化 IPC

沿用现有 `ThingOS` 和基础 IoT 初始化。在 `IoTCallback.onMQTTStatusChanged` 收到 `IoTSDKManager.STATUS_MQTT_ONLINE` 后，在工作线程调用 `ThingIPC.getInstance().init(config, listener)`。

`ThingIPC`、`IPCConfig` 和 `IPCEventListener` 位于 `com.tuya.smartai.iot_sdk.ipc` 包。

| 配置 / 条件 | 要求 |
|---|---|
| `IPCConfig.recvDir` | 已创建、可写的接收目录。 |
| `IPCConfig.enableVideo` | 设置为 `true`。 |
| 视频参数 | `videoWidth`、`videoHeight`、`videoFps`、`videoGop`、`videoBitrateKbps` 与设备实际的上行编码参数一致。 |
| `listener` | 首次初始化传入非空的 `IPCEventListener`，实现下行帧回调。 |
| 初始化结果 | 返回 `ThingIPC.RESULT_OK`（`0`）才继续送帧；失败时记录原始错误码。重复上线或页面重入不重复初始化共享通道。 |

下行通道由 App 的媒体控制命令启动；设备初始化时不要额外常驻调用 `speakStart()`、`displayStart()`。`onLiveVideoStart` 表示 App 开始拉取设备上行视频，下行视频是否到达应以 `onVideoFrame` 为准。

### 3.2 接收与播放手机音视频

在传给 `init` 的 `IPCEventListener` 中实现以下回调：

```kotlin
fun onVideoFrame(
    codec: Int, frameType: Int, width: Int, height: Int, fps: Int,
    data: ByteArray, ptsUs: Long,
)

fun onAudioFrame(
    codec: Int, sampleRate: Int, databits: Int, channel: Int,
    data: ByteArray, ptsUs: Long,
)
```

回调返回编码数据，运行在 SDK 工作线程。将数据交给应用媒体队列后快速返回，不在回调中直接操作 View 或进行耗时解码。应用根据当前业务会话控制媒体消费；播放资源就绪后才送入解码器，结束后到达的帧直接丢弃。

**视频：`onVideoFrame` → H.264 解码 → Surface 显示。**

| 步骤 | 必要处理 |
|---|---|
| 准备显示 | 等待 `SurfaceView` / `TextureView` 的 Surface 可用；选择支持当前 H.264 参数的 `MediaCodec` 解码器，宽高使用实际码流参数。 |
| 准备首帧 | 解析 Annex-B 数据，取得 SPS / PPS，并等待 IDR 关键帧；参数集或关键帧未齐时不从普通 P/B 帧开始解码。 |
| 配置解码 | 使用 `video/avc`，将带起始码的 SPS、PPS 分别写入 `MediaFormat` 的 `csd-0`、`csd-1`，然后 `configure(format, surface, null, 0)`、`start()`。已放入格式的参数集无需另作初始化缓冲重复提交。 |
| 送帧与显示 | 按顺序向输入缓冲提交完整编码帧，`queueInputBuffer` 的时间戳使用 `ptsUs`；持续取出输出缓冲并调用 `releaseOutputBuffer(index, true)` 显示。不要把编码数据当作图片或 YUV 直接绘制。 |
| 重建与积压 | Surface 销毁时先停止解码再释放 Surface；重建或码流参数变化后重新配置，等待参数集和 IDR。队列设置容量上限，丢失参考帧后重新等待关键帧。 |

接口和参数集规则见 [Android MediaCodec 文档](https://developer.android.com/reference/android/media/MediaCodec#CSD)。

**音频：`onAudioFrame` → G711U 解码为 PCM16 → AudioTrack 播放。**

1. 校验编码和音频参数。本版本常规下行为 `codec=105`、8000 Hz、`channel=0`（单声道）；将 G.711 μ-law 数据解码为有符号 PCM16，每个编码字节对应一个 16 位采样。`databits=16` 不表示回调数据已经解码。
2. 创建 `AudioTrack`，使用 `8000` Hz、`CHANNEL_OUT_MONO`、`ENCODING_PCM_16BIT`、`MODE_STREAM`；缓冲区不小于有效的 `getMinBufferSize` 返回值，确认状态为 `STATE_INITIALIZED` 后调用 `play()`。使用字节缓冲时按本机字节序存放 PCM16。
3. 在播放线程顺序 `write` 解码后的 PCM，处理部分写入和错误返回，避免阻塞 SDK 回调；根据产品选择扬声器 / 耳机输出及音量，不能把收到音频帧等同于已经出声。同步采集与外放时，回声消除由设备音频方案处理。

播放接口见 [Android AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)，PCM 表示见 [AudioFormat](https://developer.android.com/reference/android/media/AudioFormat#ENCODING_PCM_16BIT)。G711U 解码可复用已有实现，或参考 [ITU-T G.711](https://www.itu.int/rec/T-REC-G.711/en)及 [G.191 软件工具库下载](https://www.itu.int/rec/T-REC-G.191-202207-I/en)中的 G.711 C 实现；后者是算法参考，需适配 Android 工程，不是即用的 Android 播放组件。

**挂断与释放：**先关闭本次媒体消费入口、清空待播队列，再等待在途解码 / 写入结束，释放 `MediaCodec`；`AudioTrack` 使用 `pause()`、`flush()` 清除未播放声音后再 `release()`。停止操作与媒体处理串行执行，避免释放后继续送帧或写入；旧回调不得自动重建播放器。共享 `ThingIPC` 的释放条件见第 4 节。

`MediaCodec`、`AudioTrack` 属于 Android 系统 API，无需另加播放器 AAR；应用仍需提供下行解码封装、G711U 解码实现、播放队列和显示视图。这些实现不包含在设备 SDK 和公开 sample 中。

## 4. 媒体接口与格式

| 接口 / 数据 | 要求 |
|---|---|
| `appendVideoFrame(data, keyFrame, ptsUs, timestampMs)` | 设备上行传入 H.264 Annex-B 完整编码帧；IDR 设置 `keyFrame=true`，包含所需 SPS/PPS。 |
| `appendAudioFrame(data, ptsUs)` | 设备上行传入 G711U，8 kHz、单声道；采集的 PCM16 需先编码。 |
| 时间戳 | `ptsUs` 单位为微秒，音视频使用同一时间基准；`timestampMs` 仍需传参，本版本不以此参数实现墙钟同步。 |
| 下行视频 | `codec=2` 为 H.264，`frameType=0` 为 P/B 帧、`1` 为 I 帧；按回调尺寸、参数集和关键帧解码。 |
| 下行音频 | 常规协商为 `codec=105`（G711U）、8000 Hz；`databits=16` 不表示数据已是 PCM16。 |
| 音频 `channel` | 枚举值：`0` 为单声道，`1` 为双声道，不是声道数量。 |

本版本下行视频能力为 H.264 Baseline、640×480；`IPCConfig.videoWidth/videoHeight` 配置的是设备上行。设备收到不支持的编码格式时应记录并跳过，不按其他格式强行解码。

原有上行继续处理 `onLiveVideoStart/onLiveVideoStop`、`onRequestVideoKeyFrame` 和 `onVideoLoadAdjust`。送帧接口返回 `0` 表示本次提交成功，非 `0` 需处理错误。

通道不再使用时，先停止新增媒体任务；移除监听 `setListener(null)` 后，等待已进入的回调和应用采集、送帧、解码任务结束，再调用 `deinit()` 并检查返回值。共享通道仍有使用方时不要释放；重新初始化时传入非空监听器并重建已释放的播放资源。

## 5. 设备 Sample 与文件依赖

参考官方 [`sample-iot-ipc/src/main`](https://github.com/tuya/TuyaOS_android_platform/tree/main-ipc/sample-iot-ipc/src/main)。它提供基础初始化与上行采集，**未包含设备通话应答及下行解码播放**；设备依赖按第 2 节升级，下行播放见第 3.2 节，呼叫业务配套说明见附录 B。

| 入口 / 文件 | 用途及配套依赖 |
|---|---|
| `InitApp.kt`、`MainActivity.kt` | 基础初始化、设备激活和 IPC 启动。 |
| `CameraStreamer.kt`、`GlMirrorPipeline.kt` | 摄像头采集与上行编码，需保留二者及 AndroidX Core 依赖。 |
| 复制示例页面时 | 一并保留 `activity_main.xml`、`utils/QrCodeUtil.kt`、ZXing 依赖、Gradle、Manifest 和引用资源。 |

已有上行实现可继续复用。本次新增下行播放需在应用工程中提供视频解码器及显示视图、G711U 解码器和音频播放器；文件名由工程自行定义。已有对应模块可直接适配回调，无需另建一套示例文件。呼叫应答模块按附录 B 复用或接入。

## 6. 设备端接入检查

1. 最终 APK 使用 `2.3.5-ipc`，包含目标 ABI 和必要依赖；设备上线后 IPC 初始化成功，原有上行预览和声音正常。
2. App 启动发送后，设备实际收到 `onVideoFrame` / `onAudioFrame`，编码、采样率、声道和时间戳按第 4 节处理。
3. 设备屏幕实际显示手机画面，设备音频输出实际播放手机声音；双向传输期间原有上行仍正常。
4. Surface 重建、解码重启和再次通话后，视频能从参数集及有效关键帧恢复；没有持续黑屏、杂音或播放队列积压。
5. 挂断或异常结束后停止本次媒体消费，释放解码与播放资源；迟到回调不重新启动播放，再次接入可正常工作。

## 附录 A：App 侧联调配合

手机使用 Smart App SDK 的 IPC / 通话模块，版本与设备 SDK 分别管理。App 基础集成参考[智能生活 App SDK 初始化](https://developer.tuya.com/cn/docs/app-development/integrated?id=Ka69nt96cw0uj)及[安卓版 IPC SDK 集成](https://developer.tuya.com/cn/docs/app-development/preparation?id=Ka8j28bt6i7eo)；调用流程参考[带屏摄像机](https://developer.tuya.com/cn/docs/app-development/videocall?id=Kdfiptfe3joyp)和[双向音视频通话](https://developer.tuya.com/cn/docs/app-development/two-way-av-call?id=Ke7sa7e8tqpgi)。

| 联调步骤 | App 侧需要完成 |
|---|---|
| 准备呼叫 | 登录并取得目标设备访问权限，调用 `fetchSupportVideoCall` 确认能力，完成通话模块注册、消息监听和页面状态关联。官方 sample 的带屏 IPC 标识为 `sp_dpsxj`，`extra` 使用 `bizType="screen_ipc"`、`category="sp_dpsxj"`、`channelType=ThingCallChannel.ALL.value`。 |
| 建立连接 | 创建本次通话的 P2P 对象，绑定设备预览视图和本地采集视图，调用 `connect`，成功后调用 `startPreview`。 |
| 发送手机音视频 | 同时满足设备已接听 `ANSWER`、P2P 已连接、预览成功及摄像头 / 麦克风已授权后，调用 `startAudioTalk`；`startVideoCapture()` 返回 `true` 后再调用 `startVideoTalk`，检查发送结果。单独 `startPreview` 不会发送手机画面。 |
| 结束 | 用户挂断通过 `hangupClick()` 通知通话模块；停止采集、对讲、视频发送和预览后，完成断连、监听移除及 P2P / 视图释放。页面销毁和账号退出按官方 sample 注销关联与消息监听。 |

接听、预览和授权结果的先后顺序不固定，每个入口均检查同一组启动条件；按会话去重，只启动一次。权限拒绝或发送失败时结束当前呼叫；结束后阻止迟到回调重新启动媒体，并处理在途请求的清理。

参考官方 [Android App SDK sample 的 IPC 模块](https://github.com/tuya/tuya-home-android-sdk-sample-kotlin/tree/021609b3c374844d9d5c12edebca24de2d940631/homesdk_sample/ipc)中的 `CameraVideoCallManager.kt`、`CameraVideoCallActivity.kt`、`LocalCameraMonitor.java`。使用示例页面时一并保留模块的布局、工具类、资源、Gradle 依赖、ViewBinding 和 Manifest 配置；设备 SDK 工程无需引入这些手机 App 页面文件。

## 附录 B：App 呼叫设备时的应答业务

来电、接听、拒接、挂断和心跳属于应用的呼叫业务。`ThingIPC.init` 及音视频帧回调不自动提供这套业务；已有适配的应答模块可继续使用。设备端通过 `ThingOS.getInstance().ioTSDKManager` 接入：

1. MQTT 上线后调用 `registerMqttMessageOut(308)` 并检查返回码，重连后恢复注册。
2. 在 `IoTCallback.onMqttMsg(protocol, msg)` 中筛选协议 `308`、`type="rtc"` 及对应业务，校验目标与会话标识；按协议处理响铃、接听、拒接、忙线、取消、挂断、超时及心跳。
3. 按配套协议组装应答报文，通过 `sendMqtt(308, json)` 发送。接口返回 `0` 不代表 App 已收到或媒体已接通。

用户接听时沿用来电会话标识回复 `answer`，停止响铃并准备播放资源；不要等到 `onLiveVideoStart` 或首个下行帧才应答。App 收到 `ANSWER` 后按附录 A 启动发送。重复或迟到事件按会话处理，结束后停止计时和本次媒体，不循环回发挂断消息。

**首次实现应答模块时，需配套适用于本版本、App 呼叫带屏设备场景的 MQTT 308 协议**，包括报文结构与必填字段、`curId` / `targetId` 方向、`sessionId`、`callType`、`timeout` 含义及单位、`extra` 参数、各事件报文和心跳 / 超时规则。该资料应随接入资料一并提供；尚未取得时可通过[涂鸦技术支持入口](https://developer.tuya.com/cn/docs/iot/troubleshooting?id=K9258orwg9heh)申请，注明版本和场景。此处是资料申请入口，不能代替协议正文。

没有现成应答模块及配套协议时，需先补齐呼叫业务，才能完成 App 主动呼叫的端到端联调。官网[双向视频对讲](https://developer.tuya.com/cn/docs/iot-device-dev/two-way-video-intercom?id=Kf6utujivwpix)中的 `tuya_tmm_control_*` 是 C 接口，不能直接作为本 Android AAR 的 Java / Kotlin 接口使用。
