package com.example.watchtransfer.receiver

import com.example.watchtransfer.protocol.TransferHeader
import com.example.watchtransfer.protocol.TransferProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.MessageDigest

class TransferSessionReceiverTest {
    @Test
    fun commitsFileWhenChecksumMatches() {
        val content = "hello watch".encodeToByteArray()
        val store = FakeIncomingFileStore()
        val progress = mutableListOf<Long>()

        val result = TransferSessionReceiver().receive(
            input = ByteArrayInputStream(packetBytes("note.txt", "text/plain", content)),
            store = store,
            onProgress = { progress += it.bytesReceived }
        )

        assertEquals(TransferResult.Success("Download/WatchTransfer/note.txt", content.size.toLong()), result)
        assertArrayEquals(content, store.createdFile.bytes.toByteArray())
        assertTrue(store.createdFile.committed)
        assertEquals(false, store.createdFile.aborted)
        assertEquals(content.size.toLong(), progress.last())
    }

    @Test
    fun abortsFileWhenChecksumDoesNotMatch() {
        val content = "bad payload".encodeToByteArray()
        val store = FakeIncomingFileStore()
        val packet = packetBytes("bad.txt", "text/plain", content, shaOverride = "0".repeat(64))

        val result = TransferSessionReceiver().receive(
            input = ByteArrayInputStream(packet),
            store = store,
            onProgress = {}
        )

        assertTrue(result is TransferResult.Failure)
        assertEquals(false, store.createdFile.committed)
        assertEquals(true, store.createdFile.aborted)
    }

    @Test
    fun rejectsOversizedFileBeforeCreatingOutput() {
        val content = "large".encodeToByteArray()
        val store = FakeIncomingFileStore()
        val packet = packetBytes("large.jpg", "image/jpeg", content, declaredSize = 1024L)

        val result = TransferSessionReceiver(maxFileBytes = 10L).receive(
            input = ByteArrayInputStream(packet),
            store = store,
            onProgress = {}
        )

        assertTrue(result is TransferResult.Failure)
        assertEquals(0, store.createCount)
    }

    private fun packetBytes(
        fileName: String,
        mimeType: String,
        content: ByteArray,
        declaredSize: Long = content.size.toLong(),
        shaOverride: String? = null
    ): ByteArray {
        val sha = shaOverride ?: content.sha256Hex()
        val nameBytes = fileName.encodeToByteArray()
        val mimeBytes = mimeType.encodeToByteArray()
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte()))
            data.writeByte(1)
            data.writeShort(nameBytes.size)
            data.writeShort(mimeBytes.size)
            data.writeLong(declaredSize)
            data.write(sha.encodeToByteArray())
            data.write(nameBytes)
            data.write(mimeBytes)
            data.write(content)
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private class FakeIncomingFileStore : IncomingFileStore {
        var createCount = 0
        lateinit var createdFile: FakeIncomingFile

        override fun create(header: TransferHeader): IncomingFile {
            createCount += 1
            createdFile = FakeIncomingFile("Download/WatchTransfer/${header.fileName}")
            return createdFile
        }
    }

    private class FakeIncomingFile(private val path: String) : IncomingFile {
        val bytes = ByteArrayOutputStream()
        var committed = false
        var aborted = false

        override val displayPath: String
            get() = path

        override fun openOutputStream(): OutputStream = bytes

        override fun commit() {
            committed = true
        }

        override fun abort() {
            aborted = true
        }

    }
}
