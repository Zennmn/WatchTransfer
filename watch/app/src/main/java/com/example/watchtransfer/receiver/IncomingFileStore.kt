package com.example.watchtransfer.receiver

import com.example.watchtransfer.protocol.TransferHeader
import java.io.OutputStream

interface IncomingFileStore {
    fun create(header: TransferHeader): IncomingFile
}

interface IncomingFile {
    val displayPath: String
    fun openOutputStream(): OutputStream
    fun commit()
    fun abort()
}
