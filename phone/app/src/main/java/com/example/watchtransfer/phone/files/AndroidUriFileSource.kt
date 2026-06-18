package com.example.watchtransfer.phone.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.watchtransfer.phone.transfer.TransferFileSource
import java.io.InputStream

class AndroidUriFileSource(
    private val contentResolver: ContentResolver,
    val uri: Uri
) : TransferFileSource {
    override val displayName: String by lazy { queryDisplayName() }
    override val mimeType: String by lazy { contentResolver.getType(uri) ?: "application/octet-stream" }
    override val sizeBytes: Long by lazy { querySize() }

    override fun openInputStream(): InputStream {
        return contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件")
    }

    private fun queryDisplayName(): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return "transfer-file"
    }

    private fun querySize(): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
            }
        }
        throw IllegalStateException("无法获取文件大小")
    }
}
