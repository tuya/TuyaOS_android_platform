# TuyaOS Android Platform

[📖 中文文档](./README_zh.md) | [Documentation](./docs/) | [Samples](./docs/samples/)

> Run TuyaOS on Android and explore what's possible with IoT development!

## 🎯 What is TuyaOS Android Platform?

**TuyaOS Android Platform** is a comprehensive, production-ready environment for deploying TuyaOS on Android devices. It provides:

- **Production-Ready SDK & Frameworks** for IoT device integration on Android
- **Rich Sample Applications** demonstrating real-world use cases
- **Complete Documentation** for quick integration and customization
- **Modular Architecture** for flexible extension and scalability

### Key Capabilities

| Feature | Description |
|---------|-------------|
| 🔗 **Device Connectivity** | Seamlessly connect, control, and manage IoT devices via TuyaOS |
| ☁️ **Cloud Integration** | Sync device state and data with Tuya Cloud ecosystem |
| 🤖 **AI-Powered Features** | Leverage intelligent interactions and automation capabilities |
| 📱 **Android Native** | Built for Android with native APIs and best practices |
| 🎨 **UI Components** | Pre-built UI modules for common IoT operations |
| 🔐 **Security First** | Enterprise-grade encryption and authentication |

---

## 🚀 Quick Start

### Get Started in 3 Steps:

1. **[Read the Integration Guide](./docs/integration.md)** - Set up your development environment and credentials
2. **[Explore Sample Projects](./docs/samples/)** - Learn through hands-on examples
3. **[Build Your App](./docs/integration.md#building-your-app)** - Integrate into your own project

### Installation

```bash
# Clone this repository
git clone https://github.com/tuya/TuyaOS_android_platform.git
cd TuyaOS_android_platform

# Open with Android Studio
# Configure your Tuya Cloud credentials (UUID & AuthKey)
# Build and run
```

---

## 📚 Sample Projects

Discover what you can build with TuyaOS on Android!  
Each sample is fully functional with source code, documentation, and will include visual demos (screenshots/videos).

### 🏠 Smart Home & Lighting

| Sample | What You'll Learn | Status |
|--------|------------------|--------|
| [Smart Light Control](./docs/samples/smart-light/) | Control RGB lights, brightness, scenes | ✅ Ready |
| [Multi-Device Coordination](./docs/samples/multi-device/) | Coordinate multiple devices (lights, plugs, switches) | ✅ Ready |
| [Home Automation Scenes](./docs/samples/scenes/) | Create and manage automation scenarios | 📋 Documentation |

### 📸 Media & Display

| Sample | What You'll Learn | Status |
|--------|------------------|--------|
| [Smart Photo Frame](./docs/samples/photo-frame/) | Upload, manage, and display images/videos | ✅ Ready |
| [Digital Signage](./docs/samples/digital-signage/) | Display content on connected screens | 📋 Documentation |

### 🔧 IoT Connectivity

| Sample | What You'll Learn | Status |
|--------|------------------|--------|
| [Device Pairing & Network Config](./docs/samples/device-pairing/) | Activate devices and setup Wi-Fi | ✅ Ready |
| [Real-time Data Sync](./docs/samples/data-sync/) | Handle DP data and MQTT communication | ✅ Ready |
| [Cloud State Management](./docs/samples/cloud-sync/) | Keep device state synchronized with cloud | ✅ Ready |

### 🤖 AI & Intelligence

| Sample | What You'll Learn | Status |
|--------|------------------|--------|
| [Voice Assistant Integration](./docs/samples/voice-ai/) | Voice commands and AI dialogue | 📋 Documentation |
| [Smart Recommendations](./docs/samples/ai-recommendations/) | ML-powered suggestions | 📋 Documentation |

### 🏭 Manufacturing & Testing

| Sample | What You'll Learn | Status |
|--------|------------------|--------|
| [Production Testing System](./docs/samples/prod-test/) | Device authorization and production testing workflow | ✅ Ready |

---

## 📖 Documentation

- **[Integration Guide](./docs/integration.md)** - Step-by-step setup and configuration
- **[Architecture Overview](./docs/architecture.md)** - System design and module descriptions
- **[API Reference](./docs/api-reference.md)** - Complete SDK API documentation
- **[Troubleshooting](./docs/troubleshooting.md)** - Common issues and solutions
- **[FAQ](./docs/faq.md)** - Frequently asked questions

### Language Support

- 🇬🇧 **English (Main)** - All documentation
- 🇨🇳 **中文** - [Complete Chinese Documentation](./README_zh.md)

---

## 📦 Repository Structure

```
tuya/TuyaOS_android_platform/
├── README.md                          # You are here
├── README_zh.md                       # Chinese version
├── docs/
│   ├── integration.md                 # How to integrate
│   ├── architecture.md                # System architecture
│   ├── api-reference.md               # SDK APIs
│   └── samples/                       # Sample documentation
│       ├── smart-light/
│       ├── multi-device/
│       ├── photo-frame/
│       └── ...
├── app/                               # Main demo application
├── thingiotsdk/                       # Core IoT SDK
├── thingiotsdk-aistream/              # AI streaming module
├── thingiotsdk-frame/                 # Photo frame module
├── thingiotsdk-licenseprovision/      # Device authorization
├── thingiotsdk-package/               # Packaging module
├── assets/                            # Images, videos, resources
└── gradle/                            # Build configuration
```

---

## 🔌 Supported Features

### Device Management
- ✅ Device activation and pairing
- ✅ Multi-protocol support (Wi-Fi, Bluetooth, Zigbee)
- ✅ OTA updates
- ✅ Device grouping and organization

### Data & Control
- ✅ Real-time DP (Data Point) data exchange
- ✅ MQTT messaging for reliability
- ✅ Batch operations and commands
- ✅ Historical data logging

### Cloud Integration
- ✅ Tuya Cloud API integration
- ✅ Real-time cloud synchronization
- ✅ Device sharing and family management
- ✅ Automation and scene creation

### UI & UX
- ✅ Pre-built control panels
- ✅ Customizable device interfaces
- ✅ Dark mode and theme support
- ✅ Responsive design for all screen sizes

### Security
- ✅ End-to-end encryption
- ✅ OAuth authentication
- ✅ Permission management
- ✅ Secure credential storage

---

## 🤝 Contributing

We welcome contributions! Whether you're adding new samples, improving documentation, or fixing bugs:

1. **Fork** this repository
2. **Create** a feature branch (`git checkout -b feature/my-sample`)
3. **Commit** your changes (`git commit -m 'Add smart light sample'`)
4. **Push** to the branch (`git push origin feature/my-sample`)
5. **Open** a Pull Request

See [CONTRIBUTING.md](./CONTRIBUTING.md) for detailed guidelines.

---

## 📝 License

This project is licensed under the [Apache License 2.0](./LICENSE).

```
Copyright © Tuya Inc. All rights reserved.
```

---

## 🔗 Resources

- **[Tuya Developer Platform](https://developer.tuya.com)** - Official developer hub
- **[API Documentation](https://developer.tuya.com/en/docs)** - Complete API reference
- **[IoT Development Kit](https://developer.tuya.com/en/docs/iot)** - IoT solutions
- **[Community Forum](https://community.tuya.com)** - Connect with other developers

---

## ❓ Need Help?

- 📧 **Email**: support@tuya.com
- 💬 **Community**: [Tuya Community](https://community.tuya.com)
- 🐛 **Report Issues**: [GitHub Issues](https://github.com/tuya/TuyaOS_android_platform/issues)
- 📖 **Check FAQ**: [Frequently Asked Questions](./docs/faq.md)

---

**Happy coding! 🎉**