package com.example.watchtransfer.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TransferProtocolTest {
    private val protocol = TransferProtocol()
    private val sha = "a".repeat(64)

    @Test
    fun writesAndReadsV2Header() {
        val output = ByteArrayOutputStream()

        protocol.writeHeader(
            output,
            TransferHeader(
                fileName = "note.txt",
                mimeType = "text/plain",
                fileSize = 12L,
                sha256Hex = sha
            )
        )

        val header = protocol.readHeader(ByteArrayInputStream(output.toByteArray()))

        assertEquals(TransferConstants.ProtocolVersion, header.protocolVersion)
        assertEquals("note.txt", header.fileName)
        assertEquals("text/plain", header.mimeType)
        assertEquals(12L, header.fileSize)
        assertEquals(sha, header.sha256Hex)
    }

    @Test
    fun rejectsV1Header() {
        val bytes = ByteArrayOutputStream().apply {
            write(byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte()))
            write(1)
        }.toByteArray()

        assertThrows(TransferProtocolException::class.java) {
            protocol.readHeader(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun rejectsInvalidSha() {
        assertThrows(TransferProtocolException::class.java) {
            protocol.writeHeader(
                ByteArrayOutputStream(),
                TransferHeader(
                    fileName = "bad.txt",
                    mimeType = "text/plain",
                    fileSize = 5L,
                    sha256Hex = "z".repeat(64)
                )
            )
        }
    }

    @Test
    fun writesAndReadsSuccessAck() {
        val output = ByteArrayOutputStream()

        protocol.writeAck(output, TransferAck.Success("Download/WatchTransfer/photo.jpg"))

        val ack = protocol.readAck(ByteArrayInputStream(output.toByteArray()))

        assertEquals(TransferAck.Success("Download/WatchTransfer/photo.jpg"), ack)
    }

    @Test
    fun writesAndReadsFailureAck() {
        val output = ByteArrayOutputStream()

        protocol.writeAck(output, TransferAck.Failure("文件校验失败"))

        val ack = protocol.readAck(ByteArrayInputStream(output.toByteArray()))

        assertEquals(TransferAck.Failure("文件校验失败"), ack)
    }

    @Test
    fun rejectsBadAckMagic() {
        val bytes = ByteArrayOutputStream().apply {
            write(byteArrayOf('B'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(), 'K'.code.toByte()))
            write(TransferConstants.ProtocolVersion)
            write(0)
            write(byteArrayOf(0, 0))
        }.toByteArray()

        assertThrows(TransferProtocolException::class.java) {
            protocol.readAck(ByteArrayInputStream(bytes))
        }
    }
}
