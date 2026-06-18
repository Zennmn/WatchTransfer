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
) : QueueFileSender {
    override suspend fun send(
        file: TransferFileSource,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
    ): SendFileResult = withContext(Dispatchers.IO) {
        try {
            // Read file into memory once (max 30MB) to avoid double-open of content URI
            val fileBytes = file.openInputStream().use { it.readBytes() }
            val sha = Sha256.hex(fileBytes)
            val socket = socketFactory()
            socket.useClient {
                val output = BufferedOutputStream(socket.outputStream())
                protocol.writeHeader(
                    output,
                    TransferHeader(
                        fileName = file.displayName,
                        mimeType = file.mimeType,
                        fileSize = fileBytes.size.toLong(),
                        sha256Hex = sha
                    )
                )
                // Stream from cached bytes
                var sent = 0L
                var offset = 0
                while (offset < fileBytes.size) {
                    val toWrite = minOf(bufferSize, fileBytes.size - offset)
                    output.write(fileBytes, offset, toWrite)
                    offset += toWrite
                    sent += toWrite
                    onProgress(sent, fileBytes.size.toLong())
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
