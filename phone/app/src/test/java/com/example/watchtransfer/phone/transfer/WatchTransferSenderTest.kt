package com.example.watchtransfer.phone.transfer

import com.example.watchtransfer.protocol.TransferAck
import com.example.watchtransfer.protocol.TransferProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
    fun returnsFailureWhenWatchSendsFailureAck() = runTest {
        val socket = FakeClientSocket(ackBytes = ackBytes(TransferAck.Failure("文件校验失败")))
        val sender = WatchTransferSender(socketFactory = { socket })

        val result = sender.send(
            file = FakeTransferFileSource("bad.txt", "text/plain", "bad".encodeToByteArray()),
            onProgress = { _, _ -> }
        )

        assertEquals(SendFileResult.Failure("文件校验失败"), result)
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
