package com.example.watchtransfer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.example.watchtransfer.protocol.TransferAck
import com.example.watchtransfer.protocol.TransferConstants
import com.example.watchtransfer.protocol.TransferProtocol
import com.example.watchtransfer.receiver.IncomingFileStore
import com.example.watchtransfer.receiver.TransferProgress
import com.example.watchtransfer.receiver.TransferResult
import com.example.watchtransfer.receiver.TransferSessionReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

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
    fun outputStream(): OutputStream
    fun close()
}

class BluetoothReceiveServer(
    private val socketFactory: RfcommSocketFactory,
    private val sessionReceiver: SessionReceiver,
    private val store: IncomingFileStore,
    private val protocol: TransferProtocol = TransferProtocol(),
    private val sessionTimeoutMillis: Long = DEFAULT_SESSION_TIMEOUT_MILLIS
) {
    companion object {
        private const val DEFAULT_PAUSE_AFTER_SESSION_MILLIS = 800L
        private const val DEFAULT_SESSION_TIMEOUT_MILLIS = 60_000L
    }

    fun receiveOnce(): Flow<BluetoothReceiveEvent> = callbackFlow {
        val serverSocketRef = AtomicReference<RfcommServerSocket?>()
        val socketRef = AtomicReference<ConnectedSocket?>()
        val worker = launch(Dispatchers.IO) {
            try {
                send(BluetoothReceiveEvent.Waiting)
                val serverSocket = socketFactory.openServerSocket()
                serverSocketRef.set(serverSocket)

                val socket = serverSocket.accept()
                socketRef.set(socket)

                try {
                    send(BluetoothReceiveEvent.Connected(socket.remoteName))
                    val timedOut = AtomicBoolean(false)
                    val result = try {
                        socket.closeOnSessionTimeout(sessionTimeoutMillis, timedOut) {
                            runInterruptible {
                                sessionReceiver.receive(socket.inputStream(), store) { progress ->
                                    trySend(BluetoothReceiveEvent.Progress(progress))
                                }
                            }
                        }
                    } catch (error: Exception) {
                        if (timedOut.get()) {
                            socketRef.getAndSet(null)?.closeIgnoringErrors()
                            send(BluetoothReceiveEvent.Failed("接收超时"))
                            return@launch
                        }
                        throw error
                    }
                    when (result) {
                        is TransferResult.Success -> {
                            protocol.writeAck(socket.outputStream(), TransferAck.Success(result.displayPath))
                            send(BluetoothReceiveEvent.Completed(result.displayPath, result.bytesWritten))
                        }
                        is TransferResult.Failure -> {
                            protocol.writeAck(socket.outputStream(), TransferAck.Failure(result.message))
                            send(BluetoothReceiveEvent.Failed(result.message))
                        }
                    }
                } finally {
                    socketRef.getAndSet(null)?.closeIgnoringErrors()
                }
            } catch (error: Exception) {
                if (error !is CancellationException) {
                    trySend(BluetoothReceiveEvent.Failed(error.message ?: "蓝牙接收失败"))
                }
            } finally {
                socketRef.getAndSet(null)?.closeIgnoringErrors()
                serverSocketRef.getAndSet(null)?.closeIgnoringErrors()
                close()
            }
        }

        awaitClose {
            socketRef.getAndSet(null)?.closeIgnoringErrors()
            serverSocketRef.getAndSet(null)?.closeIgnoringErrors()
            worker.cancel()
        }
    }

    fun receiveContinuously(pauseAfterSessionMillis: Long = DEFAULT_PAUSE_AFTER_SESSION_MILLIS): Flow<BluetoothReceiveEvent> = flow {
        while (currentCoroutineContext().isActive) {
            var sessionEnded = false
            receiveOnce().collect { event ->
                emit(event)
                if (event is BluetoothReceiveEvent.Completed || event is BluetoothReceiveEvent.Failed) {
                    sessionEnded = true
                }
            }
            if (sessionEnded && pauseAfterSessionMillis > 0L) {
                delay(pauseAfterSessionMillis)
            }
        }
    }
}

private fun RfcommServerSocket.closeIgnoringErrors() {
    runCatching { close() }
}

private fun ConnectedSocket.closeIgnoringErrors() {
    runCatching { close() }
}

private suspend inline fun <T> ConnectedSocket.closeOnSessionTimeout(
    timeoutMillis: Long,
    timedOut: AtomicBoolean,
    crossinline block: suspend () -> T
): T = coroutineScope {
    val timeoutJob = launch {
        delay(timeoutMillis)
        timedOut.set(true)
        closeIgnoringErrors()
    }
    val closeOnCancel = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
        if (cause is CancellationException) closeIgnoringErrors()
    }
    try {
        block()
    } finally {
        timeoutJob.cancel()
        closeOnCancel?.dispose()
    }
}

class AndroidRfcommSocketFactory(
    private val bluetoothAdapter: BluetoothAdapter
) : RfcommSocketFactory {
    @SuppressLint("MissingPermission")
    override fun openServerSocket(): RfcommServerSocket {
        val socket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
            TransferConstants.ServiceName,
            TransferConstants.WatchTransferServiceUuid
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
    override fun outputStream(): OutputStream = delegate.outputStream
    override fun close() = delegate.close()
}
