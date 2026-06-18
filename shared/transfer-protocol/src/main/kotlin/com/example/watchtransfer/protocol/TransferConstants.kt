package com.example.watchtransfer.protocol

import java.util.UUID

object TransferConstants {
    val WatchTransferServiceUuid: UUID = UUID.fromString("8d520b7a-9f29-4ce1-8a8e-f3a1e2f7b921")
    const val ServiceName: String = "WatchTransferReceiver"
    const val ProtocolVersion: Int = 2
    const val MaxFileBytes: Long = 30L * 1024L * 1024L
    const val MaxTextFieldBytes: Int = 512
    const val Sha256HexLength: Int = 64
}
