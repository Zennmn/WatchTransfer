package com.example.watchtransfer.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.watchtransfer.protocol.TransferHeader
import com.example.watchtransfer.receiver.IncomingFile
import com.example.watchtransfer.receiver.IncomingFileStore
import java.io.OutputStream

class DownloadFileStore(
    private val contentResolver: ContentResolver
) : IncomingFileStore {
    override fun create(header: TransferHeader): IncomingFile {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, header.fileName)
            put(MediaStore.Downloads.MIME_TYPE, header.mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/WatchTransfer")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建下载文件")

        return MediaStoreIncomingFile(
            contentResolver = contentResolver,
            uri = uri,
            displayName = header.fileName
        )
    }
}

class MediaStoreIncomingFile(
    private val contentResolver: ContentResolver,
    val uri: Uri,
    private val displayName: String
) : IncomingFile {
    override val displayPath: String = "Download/WatchTransfer/$displayName"

    override fun openOutputStream(): OutputStream {
        return contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法写入下载文件")
    }

    override fun commit() {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        contentResolver.update(uri, values, null, null)
    }

    override fun abort() {
        contentResolver.delete(uri, null, null)
    }
}
