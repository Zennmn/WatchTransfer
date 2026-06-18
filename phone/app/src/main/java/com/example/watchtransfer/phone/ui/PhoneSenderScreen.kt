package com.example.watchtransfer.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class DeviceUiItem(
    val name: String,
    val address: String
)

data class PhoneSenderUiState(
    val selectedDeviceName: String = "",
    val devices: List<DeviceUiItem> = emptyList(),
    val files: List<PhoneFileUiItem> = emptyList(),
    val currentStatus: String = "准备发送文件到手表",
    val currentProgress: Float = 0f,
    val totalProgress: Float = 0f,
    val canSend: Boolean = false,
    val isSending: Boolean = false
)

data class PhoneFileUiItem(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val status: String
)

@Composable
fun PhoneSenderScreen(
    state: PhoneSenderUiState,
    onPickFiles: () -> Unit,
    onPickDevice: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Watch Transfer", style = MaterialTheme.typography.headlineMedium)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("手表设备", style = MaterialTheme.typography.titleMedium)
                    Text(state.selectedDeviceName.ifBlank { "尚未选择手表" }, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onPickDevice) {
                        Text("选择手表")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("文件", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onPickFiles) {
                        Text("选择文件")
                    }
                    if (state.files.isEmpty()) {
                        Text("支持多选和系统分享", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.files, key = { it.id }) { file ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(file.sizeLabel, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(file.status, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(state.currentStatus, style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(progress = { state.currentProgress }, modifier = Modifier.fillMaxWidth())
                    LinearProgressIndicator(progress = { state.totalProgress }, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSend, enabled = state.canSend && !state.isSending, modifier = Modifier.weight(1f)) {
                    Text("发送到手表")
                }
                if (state.isSending) {
                    OutlinedButton(onClick = onCancel) {
                        Text("取消")
                    }
                }
            }
        }
    }
}
