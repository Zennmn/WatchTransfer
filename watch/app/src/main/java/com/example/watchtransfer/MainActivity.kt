package com.example.watchtransfer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.watchtransfer.bluetooth.AndroidRfcommSocketFactory
import com.example.watchtransfer.bluetooth.BluetoothReceiveServer
import com.example.watchtransfer.bluetooth.DefaultSessionReceiver
import com.example.watchtransfer.storage.DownloadFileStore
import com.example.watchtransfer.ui.ReceiverStatus
import com.example.watchtransfer.ui.ReceiverUiState
import com.example.watchtransfer.ui.ReceiverViewModel
import com.example.watchtransfer.ui.WatchReceiverScreen

class MainActivity : ComponentActivity() {
    private var lastBluetoothEnabled: Boolean? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lastBluetoothEnabled = bluetoothAdapter()?.isEnabled == true

        val permissions = requiredBluetoothPermissions()
        if (!hasPermissions(permissions)) {
            permissionLauncher.launch(permissions)
        }

        setContent {
            val bluetoothAdapter = bluetoothAdapter()
            if (!hasPermissions(permissions)) {
                WatchReceiverScreen(
                    state = ReceiverUiState(
                        status = ReceiverStatus.NeedsPermission,
                        message = "请授权蓝牙权限"
                    ),
                    onRetry = { permissionLauncher.launch(permissions) }
                )
            } else if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                WatchReceiverScreen(
                    state = ReceiverUiState(
                        status = ReceiverStatus.BluetoothOff,
                        message = "请先打开手表蓝牙"
                    ),
                    onRetry = {}
                )
            } else {
                val factory = remember {
                    val server = BluetoothReceiveServer(
                        socketFactory = AndroidRfcommSocketFactory(bluetoothAdapter),
                        sessionReceiver = DefaultSessionReceiver(),
                        store = DownloadFileStore(contentResolver)
                    )
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ReceiverViewModel(receiveOnce = { server.receiveOnce() }) as T
                        }
                    }
                }
                val viewModel: ReceiverViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(Unit) {
                    viewModel.startReceiving()
                }
                WatchReceiverScreen(
                    state = state,
                    onRetry = { viewModel.startReceiving() }
                )
            }
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = bluetoothAdapter()
        val currentEnabled = adapter?.isEnabled == true
        if (lastBluetoothEnabled != null && lastBluetoothEnabled != currentEnabled) {
            recreate()
        }
        lastBluetoothEnabled = currentEnabled
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
