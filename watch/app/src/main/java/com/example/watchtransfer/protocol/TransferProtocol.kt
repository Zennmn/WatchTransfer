package com.example.watchtransfer.protocol

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

class TransferProtocol {
    fun readHeader(input: InputStream): TransferHeader {
        val data = DataInputStream(input)
        try {
            val magic = ByteArray(4)
            data.readFully(magic)
            if (!magic.contentEquals(Magic)) {
                throw TransferProtocolException("协议标识不正确")
            }

            val version = data.readUnsignedByte()
            if (version != SupportedVersion) {
                throw TransferProtocolException("协议版本不支持")
            }

            val fileNameLength = data.readUnsignedShort()
            val mimeLength = data.readUnsignedShort()
            val fileSize = data.readLong()
            val shaBytes = ByteArray(Sha256HexLength)
            data.readFully(shaBytes)

            if (fileNameLength !in 1..MaxTextFieldBytes) {
                throw TransferProtocolException("文件名长度不合法")
            }
            if (mimeLength !in 1..MaxTextFieldBytes) {
                throw TransferProtocolException("文件类型长度不合法")
            }
            if (fileSize <= 0L) {
                throw TransferProtocolException("文件大小不合法")
            }

            val fileNameBytes = ByteArray(fileNameLength)
            val mimeBytes = ByteArray(mimeLength)
            data.readFully(fileNameBytes)
            data.readFully(mimeBytes)

            val sha256Hex = shaBytes.decodeToString().lowercase()
            if (!Sha256Regex.matches(sha256Hex)) {
                throw TransferProtocolException("校验值格式不合法")
            }

            return TransferHeader(
                protocolVersion = version,
                fileName = FileNameSanitizer.sanitize(fileNameBytes.decodeToString()),
                mimeType = mimeBytes.decodeToString().ifBlank { "application/octet-stream" },
                fileSize = fileSize,
                sha256Hex = sha256Hex
            )
        } catch (error: EOFException) {
            throw TransferProtocolException("文件头不完整", error)
        }
    }

    private companion object {
        val Magic = byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte())
        const val SupportedVersion = 1
        const val MaxTextFieldBytes = 512
        const val Sha256HexLength = 64
        val Sha256Regex = Regex("^[0-9a-f]{64}$")
    }
}

class TransferProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
