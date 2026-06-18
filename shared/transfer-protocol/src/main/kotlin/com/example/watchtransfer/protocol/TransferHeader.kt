package com.example.watchtransfer.protocol

data class TransferHeader(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val sha256Hex: String,
    val protocolVersion: Int = TransferConstants.ProtocolVersion
)
