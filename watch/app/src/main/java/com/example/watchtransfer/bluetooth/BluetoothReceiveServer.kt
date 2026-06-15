package com.example.watchtransfer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.example.watchtransfer.receiver.IncomingFileStore
import com.example.watchtransfer.receiver.TransferProgress
import com.example.watchtransfer.receiver.TransferResult
import com.example.watchtransfer.receiver.TransferSessionReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.util.UUID

val WatchTransferServiceUuid: UUID = UUID.fromString("8d520b7a-9f29-4ce1-8a8e-f3a1e2f7b921")

sealed interface BluetoothReceiveEvent {
    data object Waiting : BluetoothReceiveEvent
    data class Connected(val remoteName: String) : BluetoothReceiveEvent
    data class Progress(val progress: TransferProgress) : BluetoothReceiveEvent
    data class Completed(val displayPath: String, val bytesWritten: Long) : BluetoothReceiveEvent
    data class Failed(val message: String) : BluetoothReceiveEvent
}

interface SessionReceiver {
    fun receive(
        input: InputStream,
        store: IncomingFileStore,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult
}

class DefaultSessionReceiver(
    private val delegate: TransferSessionReceiver = TransferSessionReceiver()
) : SessionReceiver {
    override fun receive(
        input: InputStream,
        store: IncomingFileStore,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult = delegate.receive(input, store, onProgress)
}

interface RfcommSocketFactory {
    fun openServerSocket(): RfcommServerSocket
}

interface RfcommServerSocket {
    fun accept(): ConnectedSocket
    fun close()
}

interface ConnectedSocket {
    val remoteName: String
    fun inputStream(): InputStream
    fun close()
}

class BluetoothReceiveServer(
    private val socketFactory: RfcommSocketFactory,
    private val sessionReceiver: SessionReceiver,
    private val store: IncomingFileStore
) {
    fun receiveOnce(): Flow<BluetoothReceiveEvent> = channelFlow {
        send(BluetoothReceiveEvent.Waiting)
        val serverSocket = socketFactory.openServerSocket()
        try {
            val socket = serverSocket.accept()
            try {
                send(BluetoothReceiveEvent.Connected(socket.remoteName))
                val result = sessionReceiver.receive(socket.inputStream(), store) { progress ->
                    trySend(BluetoothReceiveEvent.Progress(progress))
                }
                when (result) {
                    is TransferResult.Success -> send(
                        BluetoothReceiveEvent.Completed(result.displayPath, result.bytesWritten)
                    )
                    is TransferResult.Failure -> send(BluetoothReceiveEvent.Failed(result.message))
                }
            } finally {
                socket.close()
            }
        } catch (error: Exception) {
            send(BluetoothReceiveEvent.Failed(error.message ?: "蓝牙接收失败"))
        } finally {
            serverSocket.close()
            close()
        }
    }.flowOn(Dispatchers.IO)
}

class AndroidRfcommSocketFactory(
    private val bluetoothAdapter: BluetoothAdapter
) : RfcommSocketFactory {
    @SuppressLint("MissingPermission")
    override fun openServerSocket(): RfcommServerSocket {
        val socket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
            "WatchTransferReceiver",
            WatchTransferServiceUuid
        )
        return AndroidRfcommServerSocket(socket)
    }
}

private class AndroidRfcommServerSocket(
    private val delegate: BluetoothServerSocket
) : RfcommServerSocket {
    @SuppressLint("MissingPermission")
    override fun accept(): ConnectedSocket = AndroidConnectedSocket(delegate.accept())
    override fun close() = delegate.close()
}

private class AndroidConnectedSocket(
    private val delegate: BluetoothSocket
) : ConnectedSocket {
    override val remoteName: String
        @SuppressLint("MissingPermission")
        get() = delegate.remoteDevice?.name ?: "Android 手机"

    override fun inputStream(): InputStream = delegate.inputStream
    override fun close() = delegate.close()
}
