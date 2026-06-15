package com.example.watchtransfer.bluetooth

import com.example.watchtransfer.receiver.IncomingFileStore
import com.example.watchtransfer.receiver.TransferResult
import com.example.watchtransfer.receiver.TransferSessionReceiver
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

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
        var closed = false
        override fun inputStream(): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
        override fun close() {
            closed = true
        }
    }

    private class FakeIncomingFileStore : IncomingFileStore {
        override fun create(header: com.example.watchtransfer.protocol.TransferHeader): com.example.watchtransfer.receiver.IncomingFile {
            throw AssertionError("store is not used by fake receiver")
        }
    }
}
