# 腕上传输

手机向 Wear OS 手表发送文件的蓝牙工具。

腕上传输分为两个 APK：

- 手机端：安装在 Android 手机上，用来选择文件和发送。
- 手表端：安装在 Wear OS 手表上，用来接收和保存。

文件通过 Bluetooth Classic RFCOMM 传输，不经过服务器。接收后的文件保存在手表的 `Download/WatchTransfer/` 目录。

## 下载

到 [Releases](https://github.com/Zennmn/WatchTransfer/releases) 下载：

- `WatchTransfer-phone-v0.1.0.apk`
- `WatchTransfer-watch-v0.1.0.apk`

手机端 APK 安装到手机，手表端 APK 安装到手表。

## 使用

1. 在系统蓝牙设置中先配对手机和手表。
2. 打开手表端 WatchTransfer，并保持在等待接收页面。
3. 打开手机端 WatchTransfer，授予蓝牙权限。
4. 在手机端选择目标手表。
5. 添加要发送的文件，或从其他应用分享文件到 WatchTransfer。
6. 点击发送。

传输完成后，手机端会显示保存结果。手表端会继续等待下一次接收。

## 支持的功能

- 多文件队列发送。
- 系统分享入口。
- 已配对设备选择。
- 传输进度显示。
- SHA-256 文件校验。
- 接收完成后返回成功或失败结果。

## 限制

- 只支持手机发送到手表。
- 单个文件最大 30 MiB。
- 不支持空文件。
- 传输时两端应用需要保持打开。
- 暂不支持后台传输、断点续传和传输历史。

## 常见问题

### 手机端看不到手表

先在系统蓝牙设置里完成配对。应用只列出已配对设备。

### 连接失败

确认手表端应用已经打开，并且停留在等待接收页面。手机和手表尽量靠近。

### 文件发送失败

检查文件大小、蓝牙连接和手表存储空间。可以先用一个小文本文件测试连接。

### 文件保存在哪里

默认保存到：

```text
Download/WatchTransfer/
```

## 从源码构建

需要 Android Studio 或 Android SDK、JDK 17。

```powershell
.\gradlew.bat :phone:app:assembleDebug
.\gradlew.bat :watch:app:assembleDebug
```

输出位置：

```text
phone/app/build/outputs/apk/debug/app-debug.apk
watch/app/build/outputs/apk/debug/app-debug.apk
```

## License

MIT
