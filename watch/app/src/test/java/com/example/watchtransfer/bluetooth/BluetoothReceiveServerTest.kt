package com.example.watchtransfer.bluetooth

import com.example.watchtransfer.protocol.TransferAck
import com.example.watchtransfer.receiver.IncomingFileStore
import com.example.watchtransfer.receiver.TransferResult
import com.example.watchtransfer.receiver.TransferSessionReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothReceiveServerTest {
    @Test
    fun emitsWaitingConnectedAndResultEvents() = runTest {
        val socket = FakeConnectedSocket("Pixel Phone")
        val factory = FakeRfcommSocketFactory(socket)
        val sessionReceiver = object : SessionReceiver {
            override fun receive(
                input: java.io.InputStream,
                store: IncomingFileStore,
                onProgress: (com.example.watchtransfer.receiver.TransferProgress) -> Unit
            ): TransferResult {
                return TransferResult.Success("Download/WatchTransfer/a.txt", 3L)
            }
        }
        val server = BluetoothReceiveServer(factory, sessionReceiver, FakeIncomingFileStore())

        val events = server.receiveOnce().toList()

        assertEquals(BluetoothReceiveEvent.Waiting, events[0])
        assertEquals(BluetoothReceiveEvent.Connected("Pixel Phone"), events[1])
        assertEquals(BluetoothReceiveEvent.Completed("Download/WatchTransfer/a.txt", 3L), events[2])
        assertEquals(true, socket.closed)
        assertEquals(TransferAck.Success("Download/WatchTransfer/a.txt"), socket.ack())
    }

    @Test
    fun closesServerSocketWhenCancelledWhileWaitingForConnection() = runBlocking {
        val serverSocket = BlockingServerSocket()
        val factory = object : RfcommSocketFactory {
            override fun openServerSocket(): RfcommServerSocket = serverSocket
        }
        val sessionReceiver = object : SessionReceiver {
            override fun receive(
                input: java.io.InputStream,
                store: IncomingFileStore,
                onProgress: (com.example.watchtransfer.receiver.TransferProgress) -> Unit
            ): TransferResult {
                throw AssertionError("No connection should be accepted")
            }
        }
        val server = BluetoothReceiveServer(factory, sessionReceiver, FakeIncomingFileStore())

        val job = launch(Dispatchers.Default) {
            server.receiveOnce().toList()
        }
        assertTrue(serverSocket.acceptStarted.await(1, TimeUnit.SECONDS))

        job.cancel()
        val completed = withTimeoutOrNull(1_000) {
            job.join()
            true
        } ?: false
        if (!completed) {
            serverSocket.close()
            job.join()
        }

        assertTrue(completed)
        assertTrue(serverSocket.closed.get())
    }

    @Test
    fun writesFailureAckWhenSessionFails() = runTest {
        val socket = FakeConnectedSocket("Pixel Phone")
        val factory = FakeRfcommSocketFactory(socket)
        val sessionReceiver = object : SessionReceiver {
            override fun receive(
                input: java.io.InputStream,
                store: IncomingFileStore,
                onProgress: (com.example.watchtransfer.receiver.TransferProgress) -> Unit
            ): TransferResult {
                return TransferResult.Failure("文件校验失败")
            }
        }
        val server = BluetoothReceiveServer(factory, sessionReceiver, FakeIncomingFileStore())

        val events = server.receiveOnce().toList()

        assertEquals(BluetoothReceiveEvent.Failed("文件校验失败"), events[2])
        assertEquals(TransferAck.Failure("文件校验失败"), socket.ack())
    }

    @Test
    fun emitsFailureAndClosesSocketWhenSessionTimesOut() = runBlocking {
        val socket = FakeConnectedSocket("Pixel Phone")
        val sessionStarted = CompletableFuture<Unit>()
        val factory = FakeRfcommSocketFactory(socket)
        val sessionReceiver = object : SessionReceiver {
            override fun receive(
                input: java.io.InputStream,
                store: IncomingFileStore,
                onProgress: (com.example.watchtransfer.receiver.TransferProgress) -> Unit
            ): TransferResult {
                sessionStarted.complete(Unit)
                while (!socket.closed) {
                    try {
                        Thread.sleep(10)
                    } catch (error: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("interrupted")
                    }
                }
                throw IOException("socket closed")
            }
        }
        val server = BluetoothReceiveServer(
            socketFactory = factory,
            sessionReceiver = sessionReceiver,
            store = FakeIncomingFileStore(),
            sessionTimeoutMillis = 50L
        )

        val events = server.receiveOnce().toList()

        assertTrue(sessionStarted.isDone)
        assertEquals(BluetoothReceiveEvent.Waiting, events[0])
        assertEquals(BluetoothReceiveEvent.Connected("Pixel Phone"), events[1])
        assertEquals(BluetoothReceiveEvent.Failed("接收超时"), events[2])
        assertTrue(socket.closed)
    }

    @Test
    fun receiveContinuouslyRecoversAfterFailure() = runTest {
        val first = FakeConnectedSocket("Pixel One")
        val second = FakeConnectedSocket("Pixel Two")
        val factory = QueueRfcommSocketFactory(mutableListOf(first, second))
        var callCount = 0
        val sessionReceiver = object : SessionReceiver {
            override fun receive(
                input: java.io.InputStream,
                store: IncomingFileStore,
                onProgress: (com.example.watchtransfer.receiver.TransferProgress) -> Unit
            ): TransferResult {
                callCount++
                return if (callCount == 1) {
                    TransferResult.Failure("文件校验失败")
                } else {
                    TransferResult.Success("Download/WatchTransfer/b.txt", 3L)
                }
            }
        }
        val server = BluetoothReceiveServer(factory, sessionReceiver, FakeIncomingFileStore())

        val events = server.receiveContinuously(pauseAfterSessionMillis = 0L).take(6).toList()

        assertEquals(BluetoothReceiveEvent.Waiting, events[0])
        assertEquals(BluetoothReceiveEvent.Connected("Pixel One"), events[1])
        assertTrue(events[2] is BluetoothReceiveEvent.Failed)
        assertEquals(BluetoothReceiveEvent.Waiting, events[3])
        assertEquals(BluetoothReceiveEvent.Connected("Pixel Two"), events[4])
        assertTrue(events[5] is BluetoothReceiveEvent.Completed)
    }

    @Test
    fun receiveContinuouslyStartsNextSessionAfterCompletion() = runTest {
        val first = FakeConnectedSocket("Pixel One")
        val second = FakeConnectedSocket("Pixel Two")
        val factory = QueueRfcommSocketFactory(mutableListOf(first, second))
        val sessionReceiver = object : SessionReceiver {
            override fun receive(
                input: java.io.InputStream,
                store: IncomingFileStore,
                onProgress: (com.example.watchtransfer.receiver.TransferProgress) -> Unit
            ): TransferResult {
                return TransferResult.Success("Download/WatchTransfer/a.txt", 3L)
            }
        }
        val server = BluetoothReceiveServer(factory, sessionReceiver, FakeIncomingFileStore())

        val events = server.receiveContinuously(pauseAfterSessionMillis = 0L).take(6).toList()

        assertEquals(BluetoothReceiveEvent.Waiting, events[0])
        assertEquals(BluetoothReceiveEvent.Connected("Pixel One"), events[1])
        assertEquals(BluetoothReceiveEvent.Completed("Download/WatchTransfer/a.txt", 3L), events[2])
        assertEquals(BluetoothReceiveEvent.Waiting, events[3])
        assertEquals(BluetoothReceiveEvent.Connected("Pixel Two"), events[4])
        assertEquals(BluetoothReceiveEvent.Completed("Download/WatchTransfer/a.txt", 3L), events[5])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun receiveContinuouslyDoesNotPauseBetweenDefaultSessions() = runTest {
        val first = FakeConnectedSocket("Pixel One")
        val second = FakeConnectedSocket("Pixel Two")
        val factory = QueueRfcommSocketFactory(mutableListOf(first, second))
        val sessionReceiver = object : SessionReceiver {
            override fun receive(
                input: java.io.InputStream,
                store: IncomingFileStore,
                onProgress: (com.example.watchtransfer.receiver.TransferProgress) -> Unit
            ): TransferResult {
                return TransferResult.Success("Download/WatchTransfer/a.txt", 3L)
            }
        }
        val server = BluetoothReceiveServer(factory, sessionReceiver, FakeIncomingFileStore())

        server.receiveContinuously().take(6).toList()

        assertEquals(0L, testScheduler.currentTime)
    }

    private class QueueRfcommSocketFactory(
        private val sockets: MutableList<FakeConnectedSocket>
    ) : RfcommSocketFactory {
        override fun openServerSocket(): RfcommServerSocket {
            return object : RfcommServerSocket {
                override fun accept(): ConnectedSocket = sockets.removeAt(0)
                override fun close() = Unit
            }
        }
    }

    private class FakeRfcommSocketFactory(
        private val socket: FakeConnectedSocket
    ) : RfcommSocketFactory {
        override fun openServerSocket(): RfcommServerSocket {
            return object : RfcommServerSocket {
                override fun accept(): ConnectedSocket = socket
                override fun close() = Unit
            }
        }
    }

    private class FakeConnectedSocket(
        override val remoteName: String
    ) : ConnectedSocket {
        private val output = ByteArrayOutputStream()
        var closed = false

        override fun inputStream(): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
        override fun outputStream(): java.io.OutputStream = output
        override fun close() {
            closed = true
        }

        fun ack(): com.example.watchtransfer.protocol.TransferAck {
            return com.example.watchtransfer.protocol.TransferProtocol()
                .readAck(ByteArrayInputStream(output.toByteArray()))
        }
    }

    private class FakeIncomingFileStore : IncomingFileStore {
        override fun create(header: com.example.watchtransfer.protocol.TransferHeader): com.example.watchtransfer.receiver.IncomingFile {
            throw AssertionError("store is not used by fake receiver")
        }
    }

    private class BlockingServerSocket : RfcommServerSocket {
        val acceptStarted = CountDownLatch(1)
        val closed = AtomicBoolean(false)

        override fun accept(): ConnectedSocket {
            acceptStarted.countDown()
            while (!closed.get()) {
                Thread.sleep(10)
            }
            throw IOException("server socket closed")
        }

        override fun close() {
            closed.set(true)
        }
    }
}
