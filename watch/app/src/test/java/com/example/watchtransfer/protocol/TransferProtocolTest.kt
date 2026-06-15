package com.example.watchtransfer.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class TransferProtocolTest {
    private val sha = "a".repeat(64)

    @Test
    fun readsValidHeader() {
        val bytes = headerBytes(fileName = "note.txt", mimeType = "text/plain", size = 12L, sha256 = sha)

        val header = TransferProtocol().readHeader(ByteArrayInputStream(bytes))

        assertEquals(1, header.protocolVersion)
        assertEquals("note.txt", header.fileName)
        assertEquals("text/plain", header.mimeType)
        assertEquals(12L, header.fileSize)
        assertEquals(sha, header.sha256Hex)
    }

    @Test
    fun rejectsBadMagic() {
        val bytes = headerBytes(fileName = "note.txt", mimeType = "text/plain", size = 12L, sha256 = sha)
        bytes[0] = 'B'.code.toByte()

        assertThrows(TransferProtocolException::class.java) {
            TransferProtocol().readHeader(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun rejectsZeroSize() {
        val bytes = headerBytes(fileName = "empty.txt", mimeType = "text/plain", size = 0L, sha256 = sha)

        assertThrows(TransferProtocolException::class.java) {
            TransferProtocol().readHeader(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun rejectsInvalidSha() {
        val bytes = headerBytes(fileName = "note.txt", mimeType = "text/plain", size = 12L, sha256 = "z".repeat(64))

        assertThrows(TransferProtocolException::class.java) {
            TransferProtocol().readHeader(ByteArrayInputStream(bytes))
        }
    }

    private fun headerBytes(fileName: String, mimeType: String, size: Long, sha256: String): ByteArray {
        val nameBytes = fileName.encodeToByteArray()
        val mimeBytes = mimeType.encodeToByteArray()
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte()))
            data.writeByte(1)
            data.writeShort(nameBytes.size)
            data.writeShort(mimeBytes.size)
            data.writeLong(size)
            data.write(sha256.encodeToByteArray())
            data.write(nameBytes)
            data.write(mimeBytes)
        }
        return output.toByteArray()
    }
}
