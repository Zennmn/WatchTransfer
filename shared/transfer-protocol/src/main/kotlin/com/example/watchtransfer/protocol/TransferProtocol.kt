package com.example.watchtransfer.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

class TransferProtocol {
    fun readHeader(input: InputStream): TransferHeader {
        val data = DataInputStream(input)
        try {
            val magic = ByteArray(HeaderMagic.size)
            data.readFully(magic)
            if (!magic.contentEquals(HeaderMagic)) throw TransferProtocolException("协议标识不正确")

            val version = data.readUnsignedByte()
            if (version != TransferConstants.ProtocolVersion) throw TransferProtocolException("协议版本不支持")

            val fileNameLength = data.readUnsignedShort()
            val mimeLength = data.readUnsignedShort()
            val fileSize = data.readLong()
            val shaBytes = ByteArray(TransferConstants.Sha256HexLength)
            data.readFully(shaBytes)

            validateTextLength(fileNameLength, "文件名长度不合法")
            validateTextLength(mimeLength, "文件类型长度不合法")
            if (fileSize !in 1..TransferConstants.MaxFileBytes) throw TransferProtocolException("文件大小不合法")

            val fileNameBytes = ByteArray(fileNameLength)
            val mimeBytes = ByteArray(mimeLength)
            data.readFully(fileNameBytes)
            data.readFully(mimeBytes)

            val sha256Hex = shaBytes.decodeToString().lowercase()
            validateSha(sha256Hex)

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

    fun writeHeader(output: OutputStream, header: TransferHeader) {
        val fileNameBytes = FileNameSanitizer.sanitize(header.fileName).encodeToByteArray()
        val mimeBytes = header.mimeType.ifBlank { "application/octet-stream" }.encodeToByteArray()
        validateTextLength(fileNameBytes.size, "文件名长度不合法")
        validateTextLength(mimeBytes.size, "文件类型长度不合法")
        if (header.fileSize !in 1..TransferConstants.MaxFileBytes) throw TransferProtocolException("文件大小不合法")
        validateSha(header.sha256Hex)

        DataOutputStream(output).apply {
            write(HeaderMagic)
            writeByte(TransferConstants.ProtocolVersion)
            writeShort(fileNameBytes.size)
            writeShort(mimeBytes.size)
            writeLong(header.fileSize)
            write(header.sha256Hex.lowercase().encodeToByteArray())
            write(fileNameBytes)
            write(mimeBytes)
            flush()
        }
    }

    fun readAck(input: InputStream): TransferAck {
        val data = DataInputStream(input)
        try {
            val magic = ByteArray(AckMagic.size)
            data.readFully(magic)
            if (!magic.contentEquals(AckMagic)) throw TransferProtocolException("ACK 标识不正确")

            val version = data.readUnsignedByte()
            if (version != TransferConstants.ProtocolVersion) throw TransferProtocolException("ACK 协议版本不支持")

            val status = data.readUnsignedByte()
            val messageLength = data.readUnsignedShort()
            if (messageLength > TransferConstants.MaxTextFieldBytes) throw TransferProtocolException("ACK 消息过长")

            val messageBytes = ByteArray(messageLength)
            data.readFully(messageBytes)
            val message = messageBytes.decodeToString()

            return when (status) {
                AckSuccess -> TransferAck.Success(message)
                AckFailure -> TransferAck.Failure(message)
                else -> throw TransferProtocolException("ACK 状态不合法")
            }
        } catch (error: EOFException) {
            throw TransferProtocolException("ACK 不完整", error)
        }
    }

    fun writeAck(output: OutputStream, ack: TransferAck) {
        val messageBytes = ack.message.encodeToByteArray()
        if (messageBytes.size > TransferConstants.MaxTextFieldBytes) throw TransferProtocolException("ACK 消息过长")

        DataOutputStream(output).apply {
            write(AckMagic)
            writeByte(TransferConstants.ProtocolVersion)
            writeByte(if (ack is TransferAck.Success) AckSuccess else AckFailure)
            writeShort(messageBytes.size)
            write(messageBytes)
            flush()
        }
    }

    private fun validateTextLength(length: Int, message: String) {
        if (length !in 1..TransferConstants.MaxTextFieldBytes) throw TransferProtocolException(message)
    }

    private fun validateSha(value: String) {
        if (!Sha256Regex.matches(value.lowercase())) throw TransferProtocolException("校验值格式不合法")
    }

    private companion object {
        val HeaderMagic = byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte())
        val AckMagic = byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'A'.code.toByte(), 'K'.code.toByte())
        const val AckSuccess = 0x00
        const val AckFailure = 0x01
        val Sha256Regex = Regex("^[0-9a-f]{64}$")
    }
}

class TransferProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
