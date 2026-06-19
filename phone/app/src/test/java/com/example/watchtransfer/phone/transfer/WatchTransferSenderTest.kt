package com.example.watchtransfer.phone.transfer

import com.example.watchtransfer.protocol.TransferAck
import com.example.watchtransfer.protocol.TransferProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class WatchTransferSenderTest {
    @Test
    fun writesHeaderBodyAndReadsSuccessAck() = runTest {
        val content = "hello watch".encodeToByteArray()
        val socket = FakeClientSocket(ackBytes = ackBytes(TransferAck.Success("Download/WatchTransfer/a.txt")))
        val sender = WatchTransferSender(socketFactory = { socket })
        val progress = mutableListOf<Long>()

        val result = sender.send(
            file = FakeTransferFileSource("a.txt", "text/plain", content),
            onProgress = { sent, _ -> progress += sent }
        )

        val input = ByteArrayInputStream(socket.output.toByteArray())
        val header = TransferProtocol().readHeader(input)
        val body = input.readBytes()
        assertEquals("a.txt", header.fileName)
        assertEquals(content.size.toLong(), header.fileSize)
        assertArrayEquals(content, body)
        assertEquals(SendFileResult.Success("Download/WatchTransfer/a.txt"), result)
        assertEquals(content.size.toLong(), progress.last())
        assertEquals(true, socket.closed)
    }

    @Test
    fun opensInputStreamOnlyOnce() = runTest {
        val content = "single pass".encodeToByteArray()
        val socket = FakeClientSocket(ackBytes = ackBytes(TransferAck.Success("ok")))
        var openCount = 0
        val file = object : TransferFileSource {
            override val displayName = "test.txt"
            override val mimeType = "text/plain"
            override val sizeBytes = content.size.toLong()
            override fun openInputStream(): java.io.InputStream {
                openCount++
                return ByteArrayInputStream(content)
            }
        }
        val sender = WatchTransferSender(socketFactory = { socket })

        sender.send(file = file, onProgress = { _, _ -> })

        assertEquals("File should be opened only once, but was opened $openCount times", 1, openCount)
    }

    @Test
    fun returnsFailureWhenSocketFactoryThrows() = runTest {
        val sender = WatchTransferSender(socketFactory = { throw RuntimeException("连接失败") })
        val result = sender.send(
            file = FakeTransferFileSource("a.txt", "text/plain", "abc".encodeToByteArray()),
            onProgress = { _, _ -> }
        )
        assertEquals(SendFileResult.Failure("连接失败"), result)
    }

    @Test
    fun returnsFailureWhenWatchSendsFailureAck() = runTest {
        val socket = FakeClientSocket(ackBytes = ackBytes(TransferAck.Failure("文件校验失败")))
        val sender = WatchTransferSender(socketFactory = { socket })

        val result = sender.send(
            file = FakeTransferFileSource("bad.txt", "text/plain", "bad".encodeToByteArray()),
            onProgress = { _, _ -> }
        )

        assertEquals(SendFileResult.Failure("文件校验失败"), result)
    }

    @Test
    fun rethrowsCancellationInsteadOfReturningFailure() = runTest {
        val sender = WatchTransferSender(socketFactory = {
            throw AssertionError("socketFactory should not be called")
        })
        val file = object : TransferFileSource {
            override val displayName = "cancel.txt"
            override val mimeType = "text/plain"
            override val sizeBytes = 1L
            override fun openInputStream(): java.io.InputStream {
                throw CancellationException("cancelled")
            }
        }

        try {
            sender.send(file = file, onProgress = { _, _ -> })
            fail("Expected CancellationException")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
    }

    @Test
    fun returnsFailureAndClosesSocketWhenAckReadTimesOut() = runTest {
        val socket = BlockingAckClientSocket()
        val sender = WatchTransferSender(
            socketFactory = { socket },
            sessionTimeoutMillis = 50L
        )

        val result = sender.send(
            file = FakeTransferFileSource("timeout.txt", "text/plain", "abc".encodeToByteArray()),
            onProgress = { _, _ -> }
        )

        assertEquals(SendFileResult.Failure("发送超时"), result)
        assertEquals(true, socket.closed.get())
    }

    private fun ackBytes(ack: TransferAck): ByteArray {
        val output = ByteArrayOutputStream()
        TransferProtocol().writeAck(output, ack)
        return output.toByteArray()
    }
}

private class FakeClientSocket(
    ackBytes: ByteArray
) : ClientSocket {
    val output = ByteArrayOutputStream()
    var closed = false
    private val input = ByteArrayInputStream(ackBytes)

    override fun inputStream(): java.io.InputStream = input
    override fun outputStream(): java.io.OutputStream = output
    override fun close() {
        closed = true
    }
}

private class FakeTransferFileSource(
    override val displayName: String,
    override val mimeType: String,
    private val bytes: ByteArray
) : TransferFileSource {
    override val sizeBytes: Long = bytes.size.toLong()
    override fun openInputStream(): java.io.InputStream = ByteArrayInputStream(bytes)
}

private class BlockingAckClientSocket : ClientSocket {
    private val input = BlockingInputStream()
    val closed = AtomicBoolean(false)
    val output = ByteArrayOutputStream()

    override fun inputStream(): java.io.InputStream = input
    override fun outputStream(): java.io.OutputStream = output

    override fun close() {
        closed.set(true)
        input.closeBlockingRead()
    }
}

private class BlockingInputStream : InputStream() {
    private val closed = AtomicBoolean(false)

    override fun read(): Int {
        while (!closed.get()) {
            try {
                Thread.sleep(10)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("interrupted")
            }
        }
        throw IOException("closed")
    }

    fun closeBlockingRead() {
        closed.set(true)
    }
}
