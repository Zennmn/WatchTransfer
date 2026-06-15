package com.example.watchtransfer.ui

enum class ReceiverStatus {
    NeedsPermission,
    BluetoothOff,
    Idle,
    Waiting,
    Connected,
    Receiving,
    Success,
    Failed
}

data class ReceiverUiState(
    val status: ReceiverStatus = ReceiverStatus.Idle,
    val remoteName: String = "",
    val fileName: String = "",
    val bytesReceived: Long = 0L,
    val totalBytes: Long = 0L,
    val savedPath: String = "",
    val successCount: Int = 0,
    val message: String = ""
) {
    val progressFraction: Float
        get() = if (totalBytes <= 0L) 0f else (bytesReceived.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}
