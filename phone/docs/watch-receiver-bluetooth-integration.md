# 手机端蓝牙发送端对接文档

本文档用于编写 Android 手机端发送 App，对接当前 `watch` 文件夹里的手表接收端。当前版本只做单向传输：手机发送，手表接收并保存到下载目录。

## 传输能力

- 传输方式：Bluetooth Classic RFCOMM，不是 BLE。
- 连接方式：手机主动连接已配对的手表设备。
- 接收条件：手表端 App 必须停留在“接收中”页面；离开页面后手表端会停止监听。
- 文件类型：主要面向 `.txt`、图片文件，也可发送带 MIME 类型的普通文件。
- 单次连接：每个文件单独建立 RFCOMM 连接；传完并收到 ACK 后关闭 socket。
- 文件大小上限：30 MiB。
- 手表保存位置：`Download/WatchTransfer/文件名`。
- 当前协议版本：v2，手表端接收完成后返回 ACK。手机端写入 header 和 body 后读取 ACK，可明确获知保存结果（成功路径或失败原因）。
- 多文件发送：手机端队列逐个发送，每个文件单独建立 RFCOMM 连接，不使用单连接批量协议。

## 蓝牙常量

```kotlin
private val WATCH_TRANSFER_UUID: UUID =
    UUID.fromString("8d520b7a-9f29-4ce1-8a8e-f3a1e2f7b921")

private const val WATCH_TRANSFER_SERVICE_NAME = "WatchTransferReceiver"
private const val MAX_FILE_BYTES = 30L * 1024L * 1024L
```

`WATCH_TRANSFER_SERVICE_NAME` 主要用于理解手表端服务名。手机端实际连接时使用 UUID。

## Android 权限

手机端只考虑较新的 Android 版本，建议按 Android 12+ 的蓝牙权限处理。

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission
    android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
```

运行时至少请求 `BLUETOOTH_CONNECT`。如果手机端需要扫描附近设备，再请求 `BLUETOOTH_SCAN`。如果只展示已配对设备列表，通常不需要扫描流程。

文件选择建议使用系统文件选择器、照片选择器或 SAF，这样不需要申请宽泛的存储权限。

## 设备选择流程

1. 引导用户先在系统蓝牙设置中完成手机和 OPPO Watch X 的配对。
2. 手机端读取 `BluetoothAdapter.bondedDevices`。
3. 让用户选择手表设备，可以用设备名辅助筛选，但不要只依赖设备名。
4. 开始连接前调用 `BluetoothAdapter.cancelDiscovery()`，避免发现流程影响 RFCOMM 连接速度和稳定性。
5. 使用 `device.createRfcommSocketToServiceRecord(WATCH_TRANSFER_UUID)` 创建 socket。
6. `socket.connect()` 成功后立即按下面的协议写入文件头和文件体。

## 协议格式

所有多字节数字均为 big-endian。Kotlin/Java 的 `DataOutputStream` 默认就是 big-endian。

| 字段 | 长度 | 说明 |
| --- | ---: | --- |
| magic | 4 bytes | ASCII `WTRF` |
| version | 1 byte | 当前固定为 `0x02` |
| fileNameLength | 2 bytes | UTF-8 文件名字节长度，范围 `1..512` |
| mimeTypeLength | 2 bytes | UTF-8 MIME 字节长度，范围 `1..512` |
| fileSize | 8 bytes | 文件字节数，范围 `1..31457280` |
| sha256Hex | 64 bytes | 文件体 SHA-256，小写十六进制 ASCII |
| fileName | fileNameLength | UTF-8 文件名 |
| mimeType | mimeTypeLength | UTF-8 MIME 类型，例如 `text/plain`、`image/jpeg`、`image/png` |
| body | fileSize | 原始文件字节 |

注意事项：

- 文件体的 SHA-256 必须在发送前算好，因为它写在 header 里。
- `fileSize` 必须准确。若 URI 无法直接拿到大小，建议先复制到手机端 app cache，再从缓存文件读取大小和 SHA-256。
- 文件名会在手表端做清理，非法字符会被替换。
- MIME 类型为空时可以使用 `application/octet-stream`，但手机端 UI 最好限制为文本和图片。

## 推荐发送流程

1. 用户选择一个或多个文本或图片文件。
2. 解析 `displayName`、`mimeType`、`size`。
3. 校验文件大小：必须大于 0 且不超过 30 MiB。
4. 计算文件体 SHA-256。
5. 连接已配对的手表 RFCOMM socket。
6. 写入协议头（version = `0x02`）。
7. 分块写入文件体，并更新手机端进度。
8. `flush()` 后读取手表端 ACK。
9. 根据 ACK 状态显示发送结果：成功时显示保存路径，失败时显示原因。
10. 关闭 socket。多文件时为每个文件重复步骤 5-10。

## Kotlin 发送示例

手机端使用 `shared:transfer-protocol` 模块的 `TransferProtocol` 类来写入 header 和读取 ACK，无需手动拼装字节：

```kotlin
class WatchTransferSender(
    private val context: Context
) {
    private val protocol = TransferProtocol()

    suspend fun sendUri(
        device: BluetoothDevice,
        uri: Uri,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
    ): SendResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = queryDisplayName(resolver, uri)
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val fileSize = querySize(resolver, uri)

        require(fileSize in 1..TransferConstants.MaxFileBytes) {
            "文件大小必须在 1 byte 到 30 MiB 之间"
        }

        val sha256 = Sha256.hex(resolver.openInputStream(uri)!!)

        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()

        device.createRfcommSocketToServiceRecord(TransferConstants.WatchTransferServiceUuid).use { socket ->
            socket.connect()

            val output = BufferedOutputStream(socket.outputStream)
            protocol.writeHeader(output, TransferHeader(
                fileName = fileName,
                mimeType = mimeType,
                fileSize = fileSize,
                sha256Hex = sha256
            ))

            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法打开文件" }
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var sent = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    sent += read
                    onProgress(sent, fileSize)
                }
            }
            output.flush()

            when (val ack = protocol.readAck(socket.inputStream)) {
                is TransferAck.Success -> SendResult.Success(ack.message)
                is TransferAck.Failure -> SendResult.Failure(ack.message)
            }
        }
    }
}
```

`SendResult` 定义：

```kotlin
sealed interface SendResult {
    data class Success(val savedPath: String) : SendResult
    data class Failure(val reason: String) : SendResult
}
```

需要的 import：

```kotlin
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.watchtransfer.protocol.Sha256
import com.example.watchtransfer.protocol.TransferAck
import com.example.watchtransfer.protocol.TransferConstants
import com.example.watchtransfer.protocol.TransferHeader
import com.example.watchtransfer.protocol.TransferProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.UUID
```

## 错误处理建议

- 未授予 `BLUETOOTH_CONNECT`：提示用户授权。
- 蓝牙未开启：跳转系统蓝牙设置或提示用户打开蓝牙。
- 找不到已配对手表：提示用户先在系统蓝牙里配对。
- `connect()` 失败：提示用户打开手表 App 并进入“接收中”页面后重试。
- 文件过大或大小未知：在手机端发送前拦截，不要连上后再失败。
- 发送过程中断：关闭 socket，手机端显示失败并允许重试。
- 手表端 ACK 返回失败：检查 ACK message 中的失败原因，常见为 SHA-256 校验不通过。重新选择文件并发送；如果持续出现，检查手机端 SHA-256 是否针对文件体原始字节计算。

## 联调清单

- 发送一个小 `.txt` 文件，确认手表保存到 `Download/WatchTransfer/`。
- 发送 `.jpg`、`.png`，确认文件大小和 SHA-256 校验通过。
- 手表端不在“接收中”页面时发送，确认手机端连接失败提示清楚。
- 发送超过 30 MiB 的文件，确认手机端在连接前拦截。
- 传输中关闭手表 App，确认手机端能够失败退出并释放 socket。
- 使用调试代码故意写错 SHA-256，确认手表端报校验失败。
- 发送后确认手机端收到 ACK 并显示保存路径。
- 发送多个文件，确认队列逐个发送且每个文件都收到 ACK。

## ACK 格式（v2）

手表端在接收并校验文件后返回 ACK。手机端写完 body 并 `flush()` 后读取 ACK 即可获知结果。

| 字段 | 长度 | 说明 |
| --- | ---: | --- |
| magic | 4 bytes | ASCII `WTAK` |
| version | 1 byte | 固定 `0x02` |
| status | 1 byte | `0x00` 成功，`0x01` 失败 |
| messageLength | 2 bytes | UTF-8 消息长度 |
| message | messageLength | 保存路径或失败原因 |
