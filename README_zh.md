# TuyaOS Android Platform

![](./doc/img/tuya_logo.png)

---

[🌍 English](./README.md) | **中文文档**


## 🎯 项目介绍

TuyaOS 是一种基于 RTOS、Linux、Non-OS 等内核设计的、应用于 IoT 领域、面向全连接、全场景的分布式跨平台操作系统。 [更多 TuyaOS信息](https://github.com/tuya/tuyaos-iot-wifi-ble-bk7231n) 

**TuyaOS Android Platform** 是TuyaOS的 Android 平台实现。可使开发者快速基于 Android 系统完成 AI产品的开发，包括配网、控制、设备管理等基础 OS 功能及 AI 能力。开发者可依托 Android 系统快速实现 TuyaOS 设备及 AI 产品的开发。

本工程包含多个在 Android 平台上  基于 TuyaOS的实现的 Sample

---

## 📐 整体架构

![](./doc/img/art.png)

---

## 📦 项目结构

```
TuyaOS_android_platform/
├── README.md                        # 英文文档
├── README_zh.md                     # 中文文档（当前）
├── doc/                             # 详细开发文档
│   ├── SDK使用文档 1-基础IoT文档.md    # 基础 IoT 功能文档
│   ├── SDK使用文档 2-AI使用文档.MD     # AI 能力文档
│   ├── SDK使用文档 3-相框文档.md       # 智能相框文档
│   ├── SDK使用文档 4-产测文档.md       # 产测授权文档
│   ├── 产测系统使用指南.md             # 产测操作指南
│   └── img/                         # 文档图片资源
├── sample-ai-frame/                 # 示例：AI 智能相框
├── sample-iot-tv/                   # 示例：IoT 电视控制
├── build.gradle                     # 顶层构建配置
├── settings.gradle                  # 模块配置
└── gradle/                          # Gradle 包装器
```


---

## 📖 开发文档

| 文档 | 说明 |
|------|------|
| [基础 IoT 文档](./doc/SDK使用文档%201-基础IoT文档.md) | SDK 初始化、配网、DP 点、设备控制、API 调用 |
| [AI 使用文档](./doc/SDK使用文档%202-AI使用文档.MD) | AI Stream 连接、会话管理、多模态数据收发、音频录放 |
| [相框文档](./doc/SDK使用文档%203-相框文档.md) | 相框初始化、照片管理、事件监听 |
| [产测文档](./doc/SDK使用文档%204-产测文档.md) | 产测模块集成、授权码管理 |
| [产测操作指南](./doc/产测系统使用指南.md) | 面向产测人员的操作手册 |

---

## 📚 示例项目

### 🖼️ AI 智能相框 (`sample-ai-frame`)

一个完整的 AI 智能相框示例，展示了 TuyaOS 多种能力的综合使用：

| 功能 | 说明 | 状态 |
|------|------|------|
| 设备配网 | BLE / 二维码配网，设备激活 | ✅ 就绪 |
| DP 数据通信 | 功能点数据上报与下发 | ✅ 就绪 |
| AI 对话 | 多模态 AI 对话，语音/文字/图片输入 | ✅ 就绪 |
| 文生图 / 图生图 | AI 生成图片 | ✅ 就绪 |
| 相框管理 | 云端照片上传、下载、展示 | ✅ 就绪 |
| 产测授权 | SD 卡授权码烧录 | ✅ 就绪 |

![](./doc/img/frame.jpg)



### 📺 IoT 电视控制 (`sample-iot-tv`)

一个基于 TuyaOS 的智能电视控制示例：

| 功能 | 说明 | 状态 |
|------|------|------|
| 音量控制 | 精确音量档位控制（0-100） | ✅ 完全支持 |
| 静音控制 | 静音/取消静音 | ✅ 完全支持 |
| 回到主页 | 返回电视主界面 | ✅ 完全支持 |
| 方向键控制 | 上下左右方向键（需系统权限） | ⚠️ 需系统权限 |
| 菜单/确认/返回 | 遥控器按键模拟（需系统权限） | ⚠️ 需系统权限 |

![](./doc/img/tv_img.jpg)

---

## 🔗 相关资源

- **[涂鸦开发者平台](https://developer.tuya.com)** — 官方开发者中心
- **[社区论坛](https://www.tuyaos.com/viewforum.php?f=2)** — 开发者交流社区
- **[TuyaOS 介绍](https://github.com/tuya/tuyaos-iot-wifi-ble-bk7231n)**— TuyaOS BK 平台

---

**祝开发愉快！🎉**
