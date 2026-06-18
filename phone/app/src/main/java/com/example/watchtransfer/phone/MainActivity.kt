package com.example.watchtransfer.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.example.watchtransfer.phone.ui.PhoneSenderScreen
import com.example.watchtransfer.phone.ui.PhoneSenderUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PhoneSenderScreen(
                    state = PhoneSenderUiState(),
                    onPickFiles = {},
                    onPickDevice = {},
                    onSend = {},
                    onCancel = {}
                )
            }
        }
    }
}
