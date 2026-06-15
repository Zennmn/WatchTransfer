package com.example.watchtransfer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class WatchReceiverScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsWaitingState() {
        composeRule.setContent {
            WatchReceiverScreen(
                state = ReceiverUiState(status = ReceiverStatus.Waiting, message = "等待手机连接"),
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("等待手机连接").assertIsDisplayed()
    }

    @Test
    fun showsDownloadPathAfterSuccess() {
        composeRule.setContent {
            WatchReceiverScreen(
                state = ReceiverUiState(
                    status = ReceiverStatus.Success,
                    message = "保存成功",
                    savedPath = "Download/WatchTransfer/photo.jpg",
                    successCount = 1
                ),
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("保存成功").assertIsDisplayed()
        composeRule.onNodeWithText("Download/WatchTransfer/photo.jpg").assertIsDisplayed()
    }
}
