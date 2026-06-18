# 手机端发送器与 v2 ACK 协议设计

日期：2026-06-18

## 目标

在当前手表接收端基础上，新增 Android 手机端发送 App，并把项目整理成顶层多模块工程。手机端支持从 App 内选择多个文件，或从系统分享入口接收一个或多个文件，然后通过 Bluetooth Classic RFCOMM 发送到手表端。协议升级到 v2，手表端在保存成功或失败后返回 ACK，手机端据此显示每个文件的最终结果。

本阶段不兼容旧 v1 协议，不做断点续传，不做后台常驻传输，不做完整历史记录页面。

## 工程架构

仓库整理为顶层 Gradle 多模块工程：

```text
传输/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew
  gradlew.bat
  shared/
    transfer-protocol/
  watch/
    app/
  phone/
    app/
```

`shared/transfer-protocol` 是纯 Kotlin/JVM 模块，不依赖 Android。它负责两端必须一致的协议逻辑：

- 协议常量，包括 RFCOMM UUID、magic、version、最大文件大小。
- v2 文件头编码和解码。
- v2 ACK 编码和解码。
- 文件名清理。
- SHA-256 十六进制格式校验。
- 字段长度和文件大小校验。

`watch/app` 继续负责手表端 Android 行为：蓝牙 RFCOMM server、接收文件、保存到 `Download/WatchTransfer/`、写回 ACK、圆表 Compose UI。

`phone/app` 负责手机端 Android 行为：系统文件选择和分享入口、读取已配对蓝牙设备、连接手表 RFCOMM socket、发送文件队列、等待 ACK、展示进度和结果。

## v2 协议

v2 保持单文件传输协议。一次连接只传一个文件，但传完后手表端必须返回 ACK。手机端多文件发送由队列层实现，而不是由单连接批量协议实现。

单个文件的传输顺序：

```text
手机连接手表 RFCOMM
手机发送 header
手机发送 body
手表接收、校验、保存
手表返回 ACK
双方关闭 socket
```

文件 header 格式：

| 字段 | 长度 | 说明 |
| --- | ---: | --- |
| magic | 4 bytes | ASCII `WTRF` |
| version | 1 byte | 固定 `0x02` |
| fileNameLength | 2 bytes | UTF-8 文件名字节长度，范围 `1..512` |
| mimeTypeLength | 2 bytes | UTF-8 MIME 字节长度，范围 `1..512` |
| fileSize | 8 bytes | 文件字节数，范围 `1..31457280` |
| sha256Hex | 64 bytes | 文件体 SHA-256，小写十六进制 ASCII |
| fileName | fileNameLength | UTF-8 文件名 |
| mimeType | mimeTypeLength | UTF-8 MIME 类型 |
| body | fileSize | 原始文件字节 |

ACK 格式：

| 字段 | 长度 | 说明 |
| --- | ---: | --- |
| magic | 4 bytes | ASCII `WTAK` |
| version | 1 byte | 固定 `0x02` |
| status | 1 byte | `0x00` 成功，`0x01` 失败 |
| messageLength | 2 bytes | UTF-8 消息字节长度，范围 `0..512` |
| message | messageLength | 成功路径或失败原因 |

成功 ACK 的 message 是保存路径，例如 `Download/WatchTransfer/photo.jpg`。失败 ACK 的 message 是可读原因，例如 `文件校验失败`、`无法提交下载文件`、`连接已断开`。

如果手表端收到非 v2 header、坏 magic、非法字段、文件超限、SHA-256 不匹配、保存失败或连接中断，应清理半成品，并在 socket 仍可写时尽量返回 failure ACK。

## 多文件发送队列

手机端支持一次多选或多分享，但队列内部逐个发送。每个文件独立建立连接、发送、等待 ACK、关闭连接。

队列行为：

- 用户可以一次选择多个文件。
- 文件按用户选择或系统分享传入顺序发送。
- 每个文件单独计算大小和 SHA-256。
- 当前文件失败后，默认继续发送后续文件。
- 用户可以取消队列；取消时关闭当前 socket，当前文件标记为取消，未发送文件标记为取消。
- 队列结束后显示总结果：全部成功、部分失败、全部失败或已取消。

这种设计让手表端保持简单：它只需要连续接收多个单文件连接，不需要维护批量会话状态。

## 手机端产品与 UI

手机端首页采用工具型布局。打开 App 后第一屏就是发送面板：

```text
顶部：Watch Transfer
设备区：已选择的已配对手表 / 选择手表按钮
文件区：选择文件按钮、已选文件列表、总大小
操作区：发送按钮
状态区：当前文件进度、总队列进度、每个文件成功/失败结果
```

入口：

- App 内选择：使用系统文件选择器，支持多选 txt、图片和普通文件。
- 系统分享：从相册或文件管理器分享一个或多个文件到手机端 App，进入同一发送页面。

设备选择：

- 第一版只展示已配对设备。
- 用户需要先在系统蓝牙设置中完成手机和 OPPO Watch X 的配对。
- 发送前调用 `BluetoothAdapter.cancelDiscovery()`，避免发现流程影响 RFCOMM 连接稳定性。
- App 保存最近选择的设备地址，下一次打开时自动预选；如果该设备不再处于已配对列表，则提示重新选择。

手机端状态流：

```text
Idle
→ MissingPermission / BluetoothOff / NoBondedWatch
→ Ready(files + device)
→ Preparing(file metadata + SHA-256)
→ Connecting(current file)
→ Sending(current file progress + total queue progress)
→ WaitingAck
→ FileSuccess / FileFailed
→ QueueSuccess / QueuePartialFailed / QueueFailed / Cancelled
```

UI 使用 Jetpack Compose 和 Material 3。整体风格偏工具型：信息密度适中、状态清楚、按钮和错误提示明确。第一版不做单独的历史页面，只在本次发送结果中显示文件列表和结果；后续可以扩展“最近发送”。

## 手表端改造

手表端从 v1 升级到 v2，不保留旧协议兼容分支。

需要改造：

- 使用共享协议模块解析 v2 header。
- 接收并保存一个文件。
- 校验成功后写 success ACK，message 为保存路径。
- 失败时清理半成品，并尽量写 failure ACK，message 为失败原因。
- 写完 ACK 后关闭当前连接。
- 一个文件结束后自动继续监听下一次连接，不要求用户手动点击“继续接收”。

手表端 UI 保持现在的单屏接收体验：

```text
等待手机连接
已连接
正在接收：photo1.jpg
保存成功：Download/WatchTransfer/photo1.jpg
等待下一次连接
正在接收：photo2.jpg
```

成功计数继续保留。失败时显示原因和重试入口。自动继续监听用于支撑手机端多文件队列。

## 错误处理

手机端需要明确处理：

- 缺少 `BLUETOOTH_CONNECT` 权限。
- 蓝牙未开启。
- 没有已配对设备。
- 最近选择的手表不再配对。
- 文件大小未知、为 0 或超过 30 MiB。
- URI 无法打开。
- SHA-256 计算失败。
- RFCOMM 连接失败。
- 写入过程中连接中断。
- ACK 读取超时或格式错误。
- 手表端返回 failure ACK。

手表端需要明确处理：

- 蓝牙权限缺失。
- 蓝牙关闭。
- 文件头格式错误。
- 文件大小超限。
- 文件内容不足或连接中断。
- SHA-256 校验失败。
- MediaStore 创建、写入、提交失败。
- ACK 写入失败。

失败信息应尽量面向用户可理解，但测试中保留足够精确的状态，方便定位问题。

## 测试策略

共享协议 JVM 测试：

- v2 header 编码和解码一致。
- success ACK 和 failure ACK 编码/解码一致。
- 非 v2、坏 magic、非法大小、非法 SHA、超长文件名会失败。
- 文件名清理逻辑通过。

手表端 JVM 测试：

- 成功接收后写 success ACK。
- SHA-256 错误时删除半成品并写 failure ACK。
- 连接结束后能继续等待下一次连接。
- 取消监听时关闭 server socket。

手机端 JVM 测试：

- 文件元数据读取和大小限制。
- 多文件队列全成功、部分失败、全部失败和取消。
- 发送器按顺序写 header/body，并读取 ACK。
- ACK failure 时当前文件失败，队列继续。

真机联调：

- 手表安装新版接收端并停留在接收页面。
- 手机端选择多个 txt、jpg、png 文件发送。
- 手机端显示每个文件 ACK 结果。
- 手表下载目录出现成功文件。
- 故意发送错误 SHA 或中途断开连接，确认两端错误提示可读。

最低自动验证命令：

```powershell
.\gradlew.bat :shared:transfer-protocol:test
.\gradlew.bat :watch:app:testDebugUnitTest
.\gradlew.bat :phone:app:testDebugUnitTest
.\gradlew.bat :watch:app:assembleDebug
.\gradlew.bat :phone:app:assembleDebug
```

设备连接后再运行 Android instrumented tests 和手动多文件传输验证。

## 后续扩展

后续可以在不破坏当前边界的前提下扩展：

- 最近发送历史。
- 文件重试按钮。
- 单连接批量协议。
- 断点续传。
- 手表端文件预览或删除。
- 手机端扫描附近设备和配对引导。
