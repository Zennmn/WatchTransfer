package com.example.watchtransfer.phone

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.watchtransfer.phone.bluetooth.PairedWatchDevice
import com.example.watchtransfer.phone.bluetooth.PhoneBluetoothClient
import com.example.watchtransfer.phone.bluetooth.PhoneBluetoothPermissions
import com.example.watchtransfer.phone.files.AndroidUriFileSource
import com.example.watchtransfer.phone.transfer.QueueFile
import com.example.watchtransfer.phone.transfer.SendQueueViewModel
import com.example.watchtransfer.phone.transfer.WatchTransferSender
import com.example.watchtransfer.phone.ui.DeviceUiItem
import com.example.watchtransfer.phone.ui.PhoneAppStatus
import com.example.watchtransfer.phone.ui.PhoneFileUiItem
import com.example.watchtransfer.phone.ui.PhoneSenderScreen
import com.example.watchtransfer.phone.ui.PhoneSenderUiState
import com.example.watchtransfer.phone.ui.WatchTransferPhoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WatchTransferPhoneTheme {
                var permissionGranted by remember {
                    mutableStateOf(hasBluetoothPermission())
                }
                var bluetoothRefreshKey by remember { mutableStateOf(0) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val requiredPermissions = requiredBluetoothPermissions()
                    permissionGranted = requiredPermissions.isEmpty() || requiredPermissions.all { results[it] == true }
                    bluetoothRefreshKey += 1
                }

                val bluetoothClient = remember {
                    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter: BluetoothAdapter? = manager.adapter
                    adapter?.let { PhoneBluetoothClient(it) }
                }
                var isBluetoothOn by remember { mutableStateOf(bluetoothClient?.isEnabled() == true) }
                var selectedDevice by remember { mutableStateOf<PairedWatchDevice?>(null) }
                var pairedDevices by remember { mutableStateOf<List<PairedWatchDevice>>(emptyList()) }

                fun refreshBluetoothSnapshot() {
                    permissionGranted = hasBluetoothPermission()
                    isBluetoothOn = bluetoothClient?.isEnabled() == true
                    bluetoothRefreshKey += 1
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, bluetoothClient) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshBluetoothSnapshot()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(permissionGranted, isBluetoothOn, bluetoothRefreshKey) {
                    if (permissionGranted && isBluetoothOn && bluetoothClient != null) {
                        try {
                            val devices = bluetoothClient.pairedDevices()
                            pairedDevices = devices
                            if (selectedDevice != null && devices.none { it.address == selectedDevice?.address }) {
                                selectedDevice = null
                            }
                        } catch (error: SecurityException) {
                            permissionGranted = false
                            pairedDevices = emptyList()
                            selectedDevice = null
                        }
                    } else {
                        pairedDevices = emptyList()
                        selectedDevice = null
                    }
                }

                // Determine app status
                val appStatus = when {
                    !permissionGranted -> PhoneAppStatus.MissingPermission
                    bluetoothClient == null || !isBluetoothOn -> PhoneAppStatus.BluetoothOff
                    pairedDevices.isEmpty() -> PhoneAppStatus.NoBondedWatch
                    else -> PhoneAppStatus.Ready
                }

                val sender = remember(selectedDevice) {
                    WatchTransferSender(socketFactory = {
                        val device = requireNotNull(selectedDevice) { "请选择手表" }
                        requireNotNull(bluetoothClient) { "蓝牙不可用" }.openSocket(device.device)
                    })
                }
                val factory = remember(sender) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return SendQueueViewModel(sender = sender) as T
                        }
                    }
                }
                val viewModel: SendQueueViewModel = viewModel(factory = factory)
                val queueState by viewModel.uiState.collectAsState()
                val filePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    viewModel.setFiles(uris.toQueueFiles())
                }

                LaunchedEffect(intent) {
                    val sharedUris = extractSharedUris(intent)
                    if (sharedUris.isNotEmpty()) {
                        viewModel.setFiles(sharedUris.toQueueFiles())
                    }
                }

                val screenState = PhoneSenderUiState(
                    appStatus = appStatus,
                    selectedDeviceName = selectedDevice?.name.orEmpty(),
                    devices = pairedDevices.map { DeviceUiItem(name = it.name, address = it.address) },
                    files = queueState.files.map {
                        PhoneFileUiItem(
                            id = it.id,
                            name = it.name,
                            sizeLabel = "${it.sizeBytes / 1024} KB",
                            status = it.status
                        )
                    },
                    currentStatus = queueState.currentStatus,
                    currentFileId = queueState.currentFileId,
                    currentProgress = queueState.currentProgress,
                    totalProgress = queueState.totalProgress,
                    canSend = selectedDevice != null && queueState.canSend,
                    isSending = queueState.isSending
                )

                PhoneSenderScreen(
                    state = screenState,
                    onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                    onPickDevice = {
                        // This path is used when devices.size == 1 (auto-select)
                        selectedDevice = pairedDevices.firstOrNull()
                    },
                    onDeviceSelected = { deviceItem ->
                        selectedDevice = pairedDevices.find { it.address == deviceItem.address }
                    },
                    onRemoveFile = viewModel::removeFile,
                    onSend = { viewModel.startSending() },
                    onCancel = { viewModel.cancelSending() },
                    onRequestPermission = {
                        permissionLauncher.launch(requiredBluetoothPermissions())
                    }
                )
            }
        }
    }

    private fun List<Uri>.toQueueFiles(): List<QueueFile> {
        return mapIndexed { index, uri ->
            QueueFile(
                id = "${uri}#$index",
                source = AndroidUriFileSource(contentResolver, uri)
            )
        }
    }

    private fun extractSharedUris(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                }
            }
            else -> emptyList()
        }
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        return PhoneBluetoothPermissions.requiredRuntimePermissions()
    }

    private fun hasBluetoothPermission(): Boolean {
        return requiredBluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}



