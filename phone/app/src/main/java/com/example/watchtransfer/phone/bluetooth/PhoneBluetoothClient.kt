package com.example.watchtransfer.phone.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.example.watchtransfer.phone.transfer.ClientSocket
import com.example.watchtransfer.protocol.TransferConstants
import java.io.InputStream
import java.io.OutputStream

data class PairedWatchDevice(
    val name: String,
    val address: String,
    val device: BluetoothDevice
)

class PhoneBluetoothClient(
    private val bluetoothAdapter: BluetoothAdapter
) {
    fun isEnabled(): Boolean = bluetoothAdapter.isEnabled

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedWatchDevice> {
        return bluetoothAdapter.bondedDevices
            .map { device ->
                PairedWatchDevice(
                    name = device.name ?: device.address,
                    address = device.address,
                    device = device
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    fun openSocket(device: BluetoothDevice): ClientSocket {
        bluetoothAdapter.cancelDiscovery()
        val socket = device.createRfcommSocketToServiceRecord(TransferConstants.WatchTransferServiceUuid)
        socket.connect()
        return AndroidClientSocket(socket)
    }
}

private class AndroidClientSocket(
    private val socket: BluetoothSocket
) : ClientSocket {
    override fun inputStream(): InputStream = socket.inputStream
    override fun outputStream(): OutputStream = socket.outputStream
    override fun close() = socket.close()
}
