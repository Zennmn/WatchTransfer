package com.example.watchtransfer.receiver

import com.example.watchtransfer.protocol.Sha256
import com.example.watchtransfer.protocol.TransferHeader
import com.example.watchtransfer.protocol.TransferProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

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
        val output = ByteArrayOutputStream()
        TransferProtocol().writeHeader(
            output,
            TransferHeader(
                fileName = fileName,
                mimeType = mimeType,
                fileSize = declaredSize,
                sha256Hex = shaOverride ?: Sha256.hex(content)
            )
        )
        output.write(content)
        return output.toByteArray()
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
