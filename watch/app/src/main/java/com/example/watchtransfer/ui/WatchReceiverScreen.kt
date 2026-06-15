package com.example.watchtransfer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WatchReceiverScreen(
    state: ReceiverUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(34.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProgressRing(state.progressFraction)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = titleFor(state),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detailFor(state),
                color = Color(0xFFB8C0CC),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (state.status == ReceiverStatus.Failed || state.status == ReceiverStatus.Idle) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Text("开始接收")
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(progress: Float) {
    Canvas(modifier = Modifier.size(86.dp)) {
        val strokeWidth = 8.dp.toPx()
        drawArc(
            color = Color(0xFF263142),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            size = Size(size.width, size.height),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF4CAF50),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            size = Size(size.width, size.height),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

private fun titleFor(state: ReceiverUiState): String = when (state.status) {
    ReceiverStatus.NeedsPermission -> "需要蓝牙权限"
    ReceiverStatus.BluetoothOff -> "蓝牙未开启"
    ReceiverStatus.Idle -> "准备接收"
    ReceiverStatus.Waiting -> "等待手机连接"
    ReceiverStatus.Connected -> "已连接"
    ReceiverStatus.Receiving -> state.fileName.ifBlank { "正在接收" }
    ReceiverStatus.Success -> "保存成功"
    ReceiverStatus.Failed -> "接收失败"
}

private fun detailFor(state: ReceiverUiState): String = when (state.status) {
    ReceiverStatus.Success -> state.savedPath
    ReceiverStatus.Receiving -> "${state.bytesReceived / 1024} KB / ${state.totalBytes / 1024} KB"
    ReceiverStatus.Connected -> state.remoteName
    else -> state.message
}
