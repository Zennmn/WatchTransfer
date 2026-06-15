package com.example.watchtransfer.receiver

data class TransferProgress(
    val fileName: String,
    val bytesReceived: Long,
    val totalBytes: Long
)

sealed interface TransferResult {
    data class Success(val displayPath: String, val bytesWritten: Long) : TransferResult
    data class Failure(val message: String) : TransferResult
}
