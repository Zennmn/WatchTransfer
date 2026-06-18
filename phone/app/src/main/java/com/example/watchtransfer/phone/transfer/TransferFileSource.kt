package com.example.watchtransfer.phone.transfer

import java.io.InputStream

interface TransferFileSource {
    val displayName: String
    val mimeType: String
    val sizeBytes: Long
    fun openInputStream(): InputStream
}
