# WatchTransfer

WatchTransfer 是一个 Android 多模块项目，用蓝牙经典 RFCOMM 在手机和 Wear OS 手表之间传输文件。手机端负责选择文件、选择已配对手表并逐个发送；手表端负责持续监听、接收、校验并保存到下载目录。

当前实现面向单向传输场景：手机发送，手表接收。协议版本为 v2，支持 ACK 响应、MIME 类型、SHA-256 完整性校验和多文件队列。

## 功能概览

- 手机端 `phone:app`
  - Jetpack Compose + Material 3 发送界面。
  - 支持系统文件选择器多选。
  - 支持 Android 分享入口 `ACTION_SEND` / `ACTION_SEND_MULTIPLE`。
  - 读取已配对蓝牙设备并选择目标手表。
  - 多文件队列逐个发送，每个文件独立建立 RFCOMM 连接。
  - 显示当前文件进度、整体队列状态、成功路径或失败原因。

- 手表端 `watch:app`
  - Wear OS 接收界面，展示等待、连接、接收进度、成功和失败状态。
  - 通过 RFCOMM 服务端监听手机连接。
  - 每个文件接收完成后写入 ACK，并自动回到监听状态。
  - 使用 `MediaStore.Downloads` 保存到 `Download/WatchTransfer/`。

- 共享协议 `shared:transfer-protocol`
  - 纯 Kotlin/JVM 模块，无 Android 依赖。
  - 统一维护 v2 wire format、常量、文件名清理、SHA-256 工具和 ACK 编解码。

## 项目结构

```text
WatchTransfer/
├── shared/transfer-protocol/   # 纯 Kotlin 传输协议库
├── phone/app/                  # Android 手机发送端
├── watch/app/                  # Wear OS 手表接收端
├── phone/docs/                 # 手机端对接说明
└── docs/                       # 项目分析和设计记录
```

根 Gradle 工程只包含：

```kotlin
include(":shared:transfer-protocol")
include(":watch:app")
include(":phone:app")
```

本地如存在 `手机模版/`，它只是 UI 刷新时用过的视觉参考工程，不参与根 Gradle 构建，也不作为主项目发布内容。

## 技术栈

- Kotlin 2.0.21
- Android Gradle Plugin 8.7.3
- Jetpack Compose Material 3
- AndroidX Lifecycle / ViewModel
- Kotlin Coroutines
- Bluetooth Classic RFCOMM
- MediaStore Downloads
- JUnit 4 + kotlinx-coroutines-test
- Java 17 toolchain

## 传输协议

协议版本：v2。

每个文件使用一条独立 RFCOMM 连接：

1. 手机端连接手表端 RFCOMM 服务。
2. 手机写入 `WTRF` header。
3. 手机写入文件 body。
4. 手表接收、保存并校验 SHA-256。
5. 手表写入 `WTAK` ACK。
6. 双方关闭连接，队列继续下一个文件。

关键常量：

| 常量 | 值 |
| --- | --- |
| Service UUID | `8d520b7a-9f29-4ce1-8a8e-f3a1e2f7b921` |
| Service name | `WatchTransferReceiver` |
| Protocol version | `2` |
| Max file size | `30 MiB` |
| Max text field | `512 bytes` |

ACK 成功时携带保存路径，例如 `Download/WatchTransfer/photo.jpg`；失败时携带可读错误原因。

## 环境要求

- Android Studio 或本地 Android SDK。
- JDK 17。
- 一台 Android 手机和一台支持蓝牙经典连接的 Wear OS 手表。
- 两端设备需先在系统蓝牙设置中完成配对。

如果从命令行构建，需要确保 `local.properties` 指向本机 Android SDK，例如：

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

`local.properties` 不会提交到仓库。

## 构建

Windows:

```powershell
.\gradlew.bat projects
.\gradlew.bat :phone:app:assembleDebug
.\gradlew.bat :watch:app:assembleDebug
```

macOS / Linux:

```bash
./gradlew projects
./gradlew :phone:app:assembleDebug
./gradlew :watch:app:assembleDebug
```

Debug APK 输出位置：

```text
phone/app/build/outputs/apk/debug/app-debug.apk
watch/app/build/outputs/apk/debug/app-debug.apk
```

## 测试

运行 JVM / 本地单元测试：

```powershell
.\gradlew.bat :shared:transfer-protocol:test :phone:app:testDebugUnitTest :watch:app:testDebugUnitTest
```

运行插桩测试需要连接设备或模拟器：

```powershell
adb devices
.\gradlew.bat :phone:app:connectedDebugAndroidTest :watch:app:connectedDebugAndroidTest
```

项目根路径当前包含中文字符，部分 Gradle 测试任务内置了 ASCII 临时路径同步逻辑，用于规避 Windows/JDK 对非 ASCII 测试 classpath 的兼容问题。

## 使用流程

1. 在手表上安装并打开 `watch:app`，授权蓝牙权限，保持在等待接收页面。
2. 在手机上安装并打开 `phone:app`，授权蓝牙权限。
3. 确认手机和手表已在系统蓝牙设置中配对。
4. 在手机端选择目标手表。
5. 点击添加文件，或从其他应用分享文件到 Watch Transfer。
6. 点击发送到手表。
7. 手表端保存成功后，手机端会显示 ACK 返回的保存路径。

## 当前限制

- 只支持手机到手表的单向传输。
- 每个文件独立连接，暂不支持单连接批量协议。
- 单文件最大 30 MiB。
- 发送端当前会先把文件读入内存以计算 SHA-256。
- 传输绑定前台 Activity 生命周期，尚未实现前台服务、后台持久传输或断点续传。
- 协议 v2 无版本协商，不兼容旧协议。
- UI 文案当前主要为中文硬编码，尚未资源化和本地化。

## 文档

- [项目分析报告](docs/project-analysis-report.md)
- [手机端蓝牙对接文档](phone/docs/watch-receiver-bluetooth-integration.md)
- [手机端 v2 设计记录](docs/superpowers/specs/2026-06-18-phone-sender-v2-design.md)
- [手表接收端设计记录](docs/superpowers/specs/2026-06-15-watch-receiver-design.md)

## 许可证

本仓库目前未声明开源许可证。公开发布或允许他人复用前，请先补充合适的 `LICENSE`。
