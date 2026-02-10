# Tuya Smart AI IoT SDK

涂鸦智能 tuyaos Android Platform 示例项目，提供提供多种 demo。以及源码

## 项目简介

本项目是基于涂鸦 IoT 平台的 Android SDK 示例工程，主要包含以下功能模块：

- **IoT 设备接入** - 设备激活、配网、DP 数据收发、MQTT 通信
- **AI 能力** - AI 对话、AI 流式交互等智能功能
- **相框功能** - 图片/视频上传、下载、展示及管理
- **产测系统** - 设备授权烧录、产测流程支持

## 模块结构

```
├── app/                          # 示例应用
├── thingiotsdk/                  # IoT 基础 SDK
├── thingiotsdk-aistream/         # AI 流式交互模块
├── thingiotsdk-frame/            # 相框功能模块
├── thingiotsdk-licenseprovision/ # 授权烧录模块
└── thingiotsdk-package/          # 打包模块
```

## 快速开始

1. 克隆项目到本地
2. 使用 Android Studio 打开项目
3. 配置授权码（UUID 和 AuthKey）
4. 编译运行到目标设备

## 文档索引

详细使用说明请参阅以下文档：

| 文档 | 说明 |
|------|------|
| [SDK 集成指南](doc/SDK集成指南.md) | SDK 接入与集成说明 |
| [基础 IoT 文档](doc/SDK使用文档%201-基础IoT文档.md) | 设备激活、DP 收发、MQTT 通信等基础功能 |
| [AI 使用文档](doc/SDK使用文档%202-AI使用文档.MD) | AI 对话、AI 流式交互等功能说明 |
| [AI Session 连接建立指南](doc/SDK使用文档%205-AI%20Session连接建立指南.md) | AI Session 建立连接的详细流程 |
| [相框文档](doc/SDK使用文档%203-相框文档.md) | 相框图片/视频管理功能 |
| [产测文档](doc/SDK使用文档%204-产测文档.md) | 产测流程与接口说明 |
| [产测系统使用指南](doc/产测系统使用指南.md) | 产测系统操作指南 |



## 许可证

Copyright © Tuya Inc.
