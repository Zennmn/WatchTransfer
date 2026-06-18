package com.example.watchtransfer.receiver

import com.example.watchtransfer.protocol.TransferConstants
import com.example.watchtransfer.protocol.TransferProtocol
import java.security.MessageDigest

class TransferSessionReceiver(
    private val protocol: TransferProtocol = TransferProtocol(),
    private val maxFileBytes: Long = TransferConstants.MaxFileBytes,
    private val bufferSize: Int = 8 * 1024
) {
    fun receive(
        input: java.io.InputStream,
        store: IncomingFileStore,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult {
        var incomingFile: IncomingFile? = null
        return try {
            val header = protocol.readHeader(input)
            if (header.fileSize > maxFileBytes) {
                return TransferResult.Failure("文件超过最大限制")
            }

            val target = store.create(header)
            incomingFile = target
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(bufferSize)
            var remaining = header.fileSize
            var received = 0L

            target.openOutputStream().use { output ->
                while (remaining > 0L) {
                    val readSize = minOf(buffer.size.toLong(), remaining).toInt()
                    val count = input.read(buffer, 0, readSize)
                    if (count == -1) {
                        throw IllegalStateException("连接已断开")
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    received += count
                    remaining -= count
                    onProgress(
                        TransferProgress(
                            fileName = header.fileName,
                            bytesReceived = received,
                            totalBytes = header.fileSize
                        )
                    )
                }
            }

            val actualSha = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (actualSha != header.sha256Hex) {
                target.abort()
                TransferResult.Failure("文件校验失败")
            } else {
                target.commit()
                TransferResult.Success(target.displayPath, received)
            }
        } catch (error: Exception) {
            incomingFile?.abort()
            TransferResult.Failure(error.message ?: "接收失败")
        }
    }
}
