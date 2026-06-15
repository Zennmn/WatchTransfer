package com.example.watchtransfer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchtransfer.bluetooth.BluetoothReceiveEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReceiverViewModel(
    private val receiveOnce: () -> Flow<BluetoothReceiveEvent>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState: StateFlow<ReceiverUiState> = _uiState.asStateFlow()

    private var receiveJob: Job? = null

    fun startReceiving() {
        if (receiveJob?.isActive == true) return

        receiveJob = viewModelScope.launch(dispatcher) {
            receiveOnce().collect { event ->
                reduce(event)
            }
        }
    }

    fun stopReceiving() {
        receiveJob?.cancel()
        receiveJob = null
        _uiState.value = _uiState.value.copy(status = ReceiverStatus.Idle, message = "")
    }

    private fun reduce(event: BluetoothReceiveEvent) {
        _uiState.update { current ->
            when (event) {
                BluetoothReceiveEvent.Waiting -> current.copy(
                    status = ReceiverStatus.Waiting,
                    message = "等待手机连接"
                )
                is BluetoothReceiveEvent.Connected -> current.copy(
                    status = ReceiverStatus.Connected,
                    remoteName = event.remoteName,
                    message = "已连接"
                )
                is BluetoothReceiveEvent.Progress -> current.copy(
                    status = ReceiverStatus.Receiving,
                    fileName = event.progress.fileName,
                    bytesReceived = event.progress.bytesReceived,
                    totalBytes = event.progress.totalBytes,
                    message = "正在接收"
                )
                is BluetoothReceiveEvent.Completed -> current.copy(
                    status = ReceiverStatus.Success,
                    savedPath = event.displayPath,
                    successCount = current.successCount + 1,
                    message = "保存成功"
                )
                is BluetoothReceiveEvent.Failed -> current.copy(
                    status = ReceiverStatus.Failed,
                    message = event.message
                )
            }
        }
    }
}
