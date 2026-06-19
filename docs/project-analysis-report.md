# WatchTransfer 项目分析报告

日期：2026-06-19

## 1. 项目概述

WatchTransfer 是一个 Android 多模块项目，用蓝牙经典 RFCOMM 在手机和 Wear OS 手表之间进行单向文件传输。核心目标是让手机端选择或分享一个或多个文件，并发送到手表端的 `Download/WatchTransfer/` 目录。

当前实现已经进入 v2 协议阶段，主要能力包括：

- 手机端多文件队列。
- 系统文件选择器和 Android 分享入口。
- 已配对蓝牙设备读取和目标手表选择。
- 每个文件独立 RFCOMM 连接。
- v2 header + body + ACK 响应。
- MIME 类型传递。
- SHA-256 完整性校验。
- 文件名清理。
- 手表端连续监听。
- 发送端和接收端连接会话超时防护。

当前不包含：

- 双向传输。
- 断点续传。
- 后台持久传输。
- 传输历史记录。
- 协议版本协商。
- 跨版本兼容。

## 2. 技术栈

| 领域 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.0.21 |
| 构建系统 | Gradle + Android Gradle Plugin 8.7.3 |
| UI | Jetpack Compose + Material 3 |
| 状态管理 | AndroidX ViewModel + StateFlow |
| 异步 | Kotlin Coroutines |
| 传输 | Bluetooth Classic RFCOMM |
| 存储 | MediaStore.Downloads |
| 测试 | JUnit 4、kotlinx-coroutines-test、AndroidX test |
| JVM | Java 17 |

## 3. 模块结构

```text
WatchTransfer/
├── shared/transfer-protocol/
├── phone/app/
├── watch/app/
├── phone/docs/
└── docs/
```

### shared:transfer-protocol

纯 Kotlin/JVM 模块，不依赖 Android。它提供：

- `TransferConstants`：协议版本、UUID、文件大小上限等常量。
- `TransferHeader`：手机到手表的文件 header。
- `TransferAck`：手表到手机的结果 ACK。
- `TransferProtocol`：header 和 ACK 编解码。
- `FileNameSanitizer`：文件名清理。
- `Sha256`：SHA-256 工具。

这个模块是两端共享边界，能防止手机端和手表端各自拼协议导致格式漂移。

### phone:app

手机发送端，包名 `com.example.watchtransfer.phone`，`minSdk = 26`，`targetSdk = 35`。

主要职责：

- 检查蓝牙权限和蓝牙开关。
- 读取已配对设备。
- 处理系统文件选择和分享入口。
- 管理多文件发送队列。
- 通过 RFCOMM 连接手表。
- 写入 v2 header 和文件 body。
- 读取手表 ACK 并更新 UI。

关键类：

- `MainActivity`
- `PhoneBluetoothClient`
- `PhoneBluetoothPermissions`
- `AndroidUriFileSource`
- `WatchTransferSender`
- `SendQueueViewModel`
- `PhoneSenderScreen`

### watch:app

手表接收端，包名 `com.example.watchtransfer`，`minSdk = 30`，`targetSdk = 35`。

主要职责：

- 检查蓝牙权限和蓝牙开关。
- 打开 RFCOMM 服务端 socket。
- 接收 header 和文件 body。
- 流式写入 `MediaStore.Downloads`。
- 校验 SHA-256。
- 写入成功或失败 ACK。
- 自动回到等待连接状态。

关键类：

- `MainActivity`
- `BluetoothReceiveServer`
- `TransferSessionReceiver`
- `DownloadFileStore`
- `ReceiverViewModel`
- `WatchReceiverScreen`

### 本地 UI 参考模板

本地如存在 `手机模版/`，它只是 UI 刷新时使用的视觉参考工程，不参与根 Gradle 构建，也不作为主项目发布内容。当前 `phone:app` 已吸收其中的主要界面结构，但没有引入模板工程里的 Gemini、Room、Retrofit、KSP 等依赖。

## 4. 架构与数据流

整体为手机客户端、手表服务端、共享协议库的三层结构。

```text
phone/app
  MainActivity
    -> SendQueueViewModel
    -> WatchTransferSender
    -> PhoneBluetoothClient
    -> shared TransferProtocol
    -> RFCOMM socket

watch/app
  MainActivity
    -> ReceiverViewModel
    -> BluetoothReceiveServer
    -> TransferSessionReceiver
    -> DownloadFileStore
    -> shared TransferProtocol
```

发送流程：

1. 用户在手机端选择文件，或从其他应用分享文件。
2. `AndroidUriFileSource` 将 URI 包装成 `TransferFileSource`。
3. `SendQueueViewModel` 建立队列并逐个触发发送。
4. `WatchTransferSender` 读取文件、计算 SHA-256、打开 RFCOMM socket。
5. 手机端写入 v2 header。
6. 手机端分块写入文件 body。
7. 手机端等待手表 ACK。
8. ViewModel 根据 ACK 更新队列状态。

接收流程：

1. 手表端 `BluetoothReceiveServer.receiveContinuously()` 进入等待状态。
2. 手机连接后，服务端进入 connected 状态。
3. `TransferSessionReceiver` 读取 header。
4. `DownloadFileStore` 创建 pending 下载项。
5. 接收端分块读入 body，写入输出流并增量计算 SHA-256。
6. 校验成功则 commit，失败则 abort。
7. `BluetoothReceiveServer` 写入 `TransferAck.Success` 或 `TransferAck.Failure`。
8. 会话结束后等待 800ms，继续监听下一个文件。

## 5. 协议分析

协议版本固定为 v2，所有多字节整数使用 big-endian。

Header magic 为 `WTRF`：

| 字段 | 长度 | 说明 |
| --- | ---: | --- |
| magic | 4 bytes | ASCII `WTRF` |
| version | 1 byte | 固定 `2` |
| fileNameLength | 2 bytes | UTF-8 文件名长度，1..512 |
| mimeTypeLength | 2 bytes | UTF-8 MIME 长度，1..512 |
| fileSize | 8 bytes | 1..30 MiB |
| sha256Hex | 64 bytes | 小写十六进制 SHA-256 |
| fileName | variable | UTF-8，接收端清理 |
| mimeType | variable | UTF-8，空值回退为 `application/octet-stream` |

ACK magic 为 `WTAK`：

| 字段 | 长度 | 说明 |
| --- | ---: | --- |
| magic | 4 bytes | ASCII `WTAK` |
| version | 1 byte | 固定 `2` |
| status | 1 byte | `0x00` 成功，`0x01` 失败 |
| messageLength | 2 bytes | 0..512 |
| message | variable | 保存路径或失败原因 |

协议优势：

- 手机端和手表端共享同一个编解码实现。
- ACK 能让发送端知道真实保存结果。
- SHA-256 能发现文件体损坏。
- Header 中包含 MIME 类型，保存时能写入 MediaStore。

协议限制：

- 无版本协商。
- 无能力协商，例如可用存储空间、最大文件大小、是否支持批量连接。
- 无重试、幂等 ID 或断点续传。
- 每个文件都需要重新连接。

## 6. 鲁棒性分析

### 已处理

- 文件大小上限：协议层拒绝 0 字节和超过 30 MiB 的文件。
- SHA-256 校验：接收端校验失败会 abort 并返回失败 ACK。
- 文件名清理：路径段和非法字符会被规范化。
- 发送端异常：`WatchTransferSender` 将非取消异常转为 `SendFileResult.Failure`。
- 队列异常防护：`SendQueueViewModel` 捕获发送异常，避免协程静默死亡。
- 会话超时：手机端和手表端已通过协程超时任务关闭 socket，避免连接后无限卡死。
- 取消清理：发送端取消会触发 socket 关闭，接收端 flow 取消会关闭 socket 和 server socket。
- MediaStore commit 失败：`MediaStoreIncomingFile.commit()` 会检查更新行数并抛出错误。

### 仍需改进

1. 前台服务缺失

   传输仍绑定 Activity / ViewModel 生命周期。手表屏幕关闭、应用进入后台或进程被杀时，接收会停止。真实使用中建议优先为手表端加入前台服务。

2. 发送端整文件读入内存

   `WatchTransferSender` 先读取整个文件以计算 SHA-256，再写入 socket。30 MiB 上限下可控，但仍不是低内存设备上的最优方案。更稳的方案是先复制到 cache 并计算 SHA，再从 cache 流式发送，或引入带预扫描的流式发送策略。

3. accept 等待无闲置超时

   手表端等待连接本身是长期监听设计，目前 `accept()` 会一直阻塞到连接或取消。取消路径会关闭 server socket，因此不是死锁，但如果未来需要“等待超时回到空闲态”，需要显式加入监听超时。

4. ACK 写入失败语义

   接收端保存成功但 ACK 写回失败时，手表可能显示成功，手机可能显示失败。这是 ACK 型协议常见的不一致窗口，需要未来通过会话 ID、幂等记录或传输历史缓解。

5. 孤立 pending 文件

   如果手表进程在 MediaStore pending 文件提交前被系统杀死，可能遗留不可见 pending 文件。建议启动时扫描并清理 `Download/WatchTransfer/` 下过期 pending 项。

6. UI 文案硬编码

   当前大量中文文案在 Kotlin 代码中，尚未迁移到 `strings.xml`，不利于本地化和文案统一维护。

## 7. 测试覆盖

当前测试覆盖重点集中在协议、发送核心、队列状态、接收核心和蓝牙服务端 flow。

代表性测试：

- `TransferProtocolTest`
  - header 编解码。
  - ACK 编解码。
  - 协议版本和 SHA 格式拒绝。

- `FileNameSanitizerTest`
  - 路径遍历清理。
  - 空名回退。
  - 长文件名截断。

- `WatchTransferSenderTest`
  - 成功发送并读取 ACK。
  - 失败 ACK。
  - socket 工厂异常。
  - 取消传播。
  - 发送超时。

- `SendQueueViewModelTest`
  - 全部成功。
  - 部分失败后继续。
  - 异常防护和取消路径。

- `BluetoothReceiveServerTest`
  - 等待、连接、成功、失败事件。
  - ACK 写入。
  - 连续监听。
  - 接收超时。

- `TransferSessionReceiverTest`
  - SHA 匹配提交。
  - SHA 不匹配中止。
  - 连接中断。
  - 超大文件拒绝。

测试缺口：

- 真机蓝牙端到端传输仍需要手动验证。
- `PhoneBluetoothClient` 依赖 Android 蓝牙栈，缺少单元测试。
- `MainActivity` wiring 和 Compose UI 仍主要依赖构建与人工检查。
- `DownloadFileStore.create()` 成功路径更适合用插桩测试覆盖。
- 多分块大文件和 ACK 写回失败窗口还可以补更精细的回归测试。

## 8. 构建与平台注意事项

根路径包含中文字符 `传输`。项目的 Gradle 测试任务加入了 ASCII 临时目录同步逻辑，规避 Windows 上非 ASCII classpath 导致的单元测试问题。

建议验证命令：

```powershell
.\gradlew.bat projects
.\gradlew.bat :shared:transfer-protocol:test :phone:app:testDebugUnitTest :watch:app:testDebugUnitTest
.\gradlew.bat :phone:app:assembleDebug :watch:app:assembleDebug
```

如果有设备连接，再运行：

```powershell
adb devices
.\gradlew.bat :phone:app:connectedDebugAndroidTest :watch:app:connectedDebugAndroidTest
```

## 9. 发布状态

适合作为私有 GitHub 仓库保存当前研发状态。若要公开发布，建议先处理：

- 明确 LICENSE。
- 将包名从 `com.example` 改为真实域名。
- 补充隐私说明，说明蓝牙和文件访问用途。
- 抽取用户可见字符串到资源文件。
- 增加端到端真机验证记录。
- 保持本地参考模板、生成计划和构建产物不进入仓库。

## 10. 优先级建议

短期：

1. 手表端前台服务。
2. 启动时清理孤立 pending 文件。
3. ACK 写入失败时的 UI 和日志策略。
4. 资源化中文文案。

中期：

1. 传输历史记录。
2. 单文件失败重试。
3. 更低内存的 SHA 和发送策略。
4. 端到端真机自动化或半自动化验证。

长期：

1. 协议版本协商。
2. 断点续传。
3. 批量连接或多文件单会话。
4. 双向传输。
