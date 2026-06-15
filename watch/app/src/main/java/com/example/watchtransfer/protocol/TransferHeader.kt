package com.example.watchtransfer.protocol

data class TransferHeader(
    val protocolVersion: Int,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val sha256Hex: String
)
