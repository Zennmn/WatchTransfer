package com.example.watchtransfer.phone.transfer

import com.example.watchtransfer.protocol.Sha256
import com.example.watchtransfer.protocol.TransferAck
import com.example.watchtransfer.protocol.TransferHeader
import com.example.watchtransfer.protocol.TransferProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

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
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    private val sessionTimeoutMillis: Long = DEFAULT_SESSION_TIMEOUT_MILLIS
) : QueueFileSender {
    override suspend fun send(
        file: TransferFileSource,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
    ): SendFileResult = withContext(Dispatchers.IO) {
        val timedOut = AtomicBoolean(false)
        try {
            // Read file into memory once (max 30MB) to avoid double-open of content URI.
            val fileBytes = withTimeout(sessionTimeoutMillis) {
                runInterruptible {
                    file.openInputStream().use { it.readBytes() }
                }
            }
            val sha = Sha256.hex(fileBytes)
            val socket = socketFactory()
            socket.useClientClosingOnTimeout(sessionTimeoutMillis, timedOut) {
                runInterruptible {
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
            }
        } catch (error: TimeoutCancellationException) {
            SendFileResult.Failure("发送超时")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (timedOut.get()) {
                SendFileResult.Failure("发送超时")
            } else {
                SendFileResult.Failure(error.message ?: "发送失败")
            }
        }
    }

    private suspend inline fun <T> ClientSocket.useClientClosingOnTimeout(
        timeoutMillis: Long,
        timedOut: AtomicBoolean,
        crossinline block: suspend () -> T
    ): T = coroutineScope {
        val timeoutJob = launch {
            delay(timeoutMillis)
            timedOut.set(true)
            closeIgnoringErrors()
        }
        val closeOnCancel = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) closeIgnoringErrors()
        }
        try {
            block()
        } finally {
            timeoutJob.cancel()
            closeIgnoringErrors()
            closeOnCancel?.dispose()
        }
    }

    private fun ClientSocket.closeIgnoringErrors() {
        runCatching { close() }
    }

    private companion object {
        const val DEFAULT_SESSION_TIMEOUT_MILLIS = 60_000L
    }
}
