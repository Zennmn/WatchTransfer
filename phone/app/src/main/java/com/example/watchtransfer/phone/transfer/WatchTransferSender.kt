package com.example.watchtransfer.phone.transfer

import com.example.watchtransfer.protocol.Sha256
import com.example.watchtransfer.protocol.TransferAck
import com.example.watchtransfer.protocol.TransferHeader
import com.example.watchtransfer.protocol.TransferProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream

interface ClientSocket {
    fun inputStream(): InputStream
    fun outputStream(): OutputStream
    fun close()
}

sealed interface SendFileResult {
    data class Success(val savedPath: String) : SendFileResult
    data class Failure(val message: String) : SendFileResult
}

class WatchTransferSender(
    private val socketFactory: suspend () -> ClientSocket,
    private val protocol: TransferProtocol = TransferProtocol(),
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE
) {
    suspend fun send(
        file: TransferFileSource,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
    ): SendFileResult = withContext(Dispatchers.IO) {
        try {
            val sha = file.openInputStream().use { input -> Sha256.hex(input) }
            val socket = socketFactory()
            socket.useClient {
                val output = BufferedOutputStream(socket.outputStream())
                protocol.writeHeader(
                    output,
                    TransferHeader(
                        fileName = file.displayName,
                        mimeType = file.mimeType,
                        fileSize = file.sizeBytes,
                        sha256Hex = sha
                    )
                )
                file.openInputStream().use { input ->
                    val buffer = ByteArray(bufferSize)
                    var sent = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        sent += read
                        onProgress(sent, file.sizeBytes)
                    }
                }
                output.flush()
                when (val ack = protocol.readAck(socket.inputStream())) {
                    is TransferAck.Success -> SendFileResult.Success(ack.message)
                    is TransferAck.Failure -> SendFileResult.Failure(ack.message)
                }
            }
        } catch (error: Exception) {
            SendFileResult.Failure(error.message ?: "发送失败")
        }
    }

    private inline fun <T> ClientSocket.useClient(block: () -> T): T {
        return try {
            block()
        } finally {
            close()
        }
    }
}
