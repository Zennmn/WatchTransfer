package com.example.watchtransfer.phone.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueFile(
    val id: String,
    val source: TransferFileSource
)

enum class QueueStatus {
    Idle,
    Ready,
    Sending,
    Success,
    PartialFailed,
    Failed,
    Cancelled
}

data class QueueFileUiState(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val status: String = "等待发送",
    val sentBytes: Long = 0L
)

data class SendQueueUiState(
    val files: List<QueueFileUiState> = emptyList(),
    val queueStatus: QueueStatus = QueueStatus.Idle,
    val currentStatus: String = "准备发送文件到手表",
    val currentFileId: String = "",
    val totalBytes: Long = 0L,
    val sentBytes: Long = 0L
) {
    val canSend: Boolean get() = files.isNotEmpty() && queueStatus in setOf(QueueStatus.Ready, QueueStatus.PartialFailed, QueueStatus.Failed, QueueStatus.Success)
    val isSending: Boolean get() = queueStatus == QueueStatus.Sending
    val currentProgress: Float
        get() {
            val current = files.firstOrNull { it.id == currentFileId } ?: return 0f
            return if (current.sizeBytes <= 0L) 0f else (current.sentBytes.toFloat() / current.sizeBytes.toFloat()).coerceIn(0f, 1f)
        }
    val totalProgress: Float
        get() = if (totalBytes <= 0L) 0f else (sentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

interface QueueFileSender {
    suspend fun send(
        file: TransferFileSource,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
    ): SendFileResult
}

class SendQueueViewModel(
    private val sender: QueueFileSender,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val queueFiles = mutableListOf<QueueFile>()
    private var sendJob: Job? = null
    private val _uiState = MutableStateFlow(SendQueueUiState())
    val uiState: StateFlow<SendQueueUiState> = _uiState.asStateFlow()

    fun setFiles(files: List<QueueFile>) {
        if (sendJob != null) return
        queueFiles.clear()
        queueFiles.addAll(files)
        _uiState.value = SendQueueUiState(
            files = files.map {
                QueueFileUiState(
                    id = it.id,
                    name = it.source.displayName,
                    sizeBytes = it.source.sizeBytes
                )
            },
            queueStatus = if (files.isEmpty()) QueueStatus.Idle else QueueStatus.Ready,
            totalBytes = files.sumOf { it.source.sizeBytes }
        )
    }

    fun removeFile(id: String) {
        if (sendJob != null) return
        val removed = queueFiles.removeAll { it.id == id }
        if (!removed) return
        _uiState.update { current ->
            val files = current.files.filterNot { it.id == id }
            current.copy(
                files = files,
                queueStatus = if (files.isEmpty()) QueueStatus.Idle else QueueStatus.Ready,
                currentStatus = "准备发送文件到手表",
                currentFileId = "",
                totalBytes = queueFiles.sumOf { it.source.sizeBytes },
                sentBytes = 0L
            )
        }
    }

    fun startSending() {
        if (sendJob != null || queueFiles.isEmpty()) return
        val filesToSend = queueFiles.toList()
        val job = viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(queueStatus = QueueStatus.Sending, currentStatus = "准备发送") }
            var failureCount = 0
            var totalSentBeforeCurrent = 0L

            for (file in filesToSend) {
                _uiState.update {
                    it.copy(
                        currentFileId = file.id,
                        currentStatus = "正在发送 ${file.source.displayName}",
                        files = it.files.mapFile(file.id) { item -> item.copy(status = "发送中", sentBytes = 0L) }
                    )
                }
                val result = try {
                    sender.send(file.source) { sent, _ ->
                        _uiState.update { current ->
                            current.copy(
                                sentBytes = totalSentBeforeCurrent + sent,
                                files = current.files.mapFile(file.id) { item -> item.copy(sentBytes = sent) }
                            )
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    SendFileResult.Failure(error.message ?: "发送失败")
                }
                when (result) {
                    is SendFileResult.Success -> {
                        _uiState.update {
                            it.copy(files = it.files.mapFile(file.id) { item -> item.copy(status = "保存成功", sentBytes = file.source.sizeBytes) })
                        }
                    }
                    is SendFileResult.Failure -> {
                        failureCount += 1
                        _uiState.update {
                            it.copy(files = it.files.mapFile(file.id) { item -> item.copy(status = result.message) })
                        }
                    }
                }
                totalSentBeforeCurrent += file.source.sizeBytes
            }

            val finalStatus = when {
                failureCount == 0 -> QueueStatus.Success
                failureCount == filesToSend.size -> QueueStatus.Failed
                else -> QueueStatus.PartialFailed
            }
            _uiState.update {
                it.copy(
                    queueStatus = finalStatus,
                    currentStatus = when (finalStatus) {
                        QueueStatus.Success -> "全部发送成功"
                        QueueStatus.Failed -> "全部发送失败"
                        QueueStatus.PartialFailed -> "部分文件发送失败"
                        else -> it.currentStatus
                    },
                    currentFileId = "",
                    sentBytes = it.totalBytes
                )
            }
        }
        sendJob = job
        job.invokeOnCompletion {
            if (sendJob === job) {
                sendJob = null
            }
        }
    }

    fun cancelSending() {
        sendJob?.cancel()
        _uiState.update {
            it.copy(
                queueStatus = QueueStatus.Cancelled,
                currentStatus = "已取消",
                files = it.files.map { file ->
                    if (file.status == "等待发送" || file.status == "发送中") file.copy(status = "已取消") else file
                }
            )
        }
    }

    private fun List<QueueFileUiState>.mapFile(
        id: String,
        transform: (QueueFileUiState) -> QueueFileUiState
    ): List<QueueFileUiState> {
        return map { file -> if (file.id == id) transform(file) else file }
    }
}
