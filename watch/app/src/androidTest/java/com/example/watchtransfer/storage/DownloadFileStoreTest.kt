package com.example.watchtransfer.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.watchtransfer.protocol.TransferHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadFileStoreTest {
    @Test
    fun writesCommittedFileToWatchTransferDownloadFolder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DownloadFileStore(context.contentResolver)
        val header = TransferHeader(
            fileName = "instrumented-note.txt",
            mimeType = "text/plain",
            fileSize = 5L,
            sha256Hex = "a".repeat(64)
        )

        val file = store.create(header) as MediaStoreIncomingFile
        file.openOutputStream().use { output ->
            output.write("hello".encodeToByteArray())
        }
        file.commit()

        assertEquals("Download/WatchTransfer/instrumented-note.txt", file.displayPath)
        assertTrue(file.uri.toString().isNotBlank())

        context.contentResolver.delete(file.uri ?: error("Expected committed file uri"), null, null)
    }
}
