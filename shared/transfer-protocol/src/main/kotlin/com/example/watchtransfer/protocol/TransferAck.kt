package com.example.watchtransfer.protocol

sealed interface TransferAck {
    val message: String

    data class Success(override val message: String) : TransferAck
    data class Failure(override val message: String) : TransferAck
}
