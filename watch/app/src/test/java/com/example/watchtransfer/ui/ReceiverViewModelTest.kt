package com.example.watchtransfer.ui

import com.example.watchtransfer.bluetooth.BluetoothReceiveEvent
import com.example.watchtransfer.receiver.TransferProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun mapsReceiveEventsIntoUiState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = ReceiverViewModel(
            receiveOnce = {
                flow {
                    emit(BluetoothReceiveEvent.Waiting)
                    emit(BluetoothReceiveEvent.Connected("Pixel Phone"))
                    emit(BluetoothReceiveEvent.Completed("Download/WatchTransfer/a.txt", 3L))
                }
            },
            dispatcher = dispatcher
        )

        viewModel.startReceiving()
        advanceUntilIdle()

        assertEquals(ReceiverStatus.Success, viewModel.uiState.value.status)
        assertEquals("Download/WatchTransfer/a.txt", viewModel.uiState.value.savedPath)
        assertEquals(1, viewModel.uiState.value.successCount)
    }

    @Test
    fun recordsFailureMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = ReceiverViewModel(
            receiveOnce = {
                flow {
                    emit(BluetoothReceiveEvent.Failed("文件校验失败"))
                }
            },
            dispatcher = dispatcher
        )

        viewModel.startReceiving()
        advanceUntilIdle()

        assertEquals(ReceiverStatus.Failed, viewModel.uiState.value.status)
        assertEquals("文件校验失败", viewModel.uiState.value.message)
    }

    @Test
    fun mapsProgressEventIntoReceivingState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = ReceiverViewModel(
            receiveOnce = {
                flow {
                    emit(BluetoothReceiveEvent.Progress(
                        TransferProgress(
                            fileName = "photo.jpg",
                            bytesReceived = 500L,
                            totalBytes = 1000L
                        )
                    ))
                }
            },
            dispatcher = dispatcher
        )

        viewModel.startReceiving()
        advanceUntilIdle()

        assertEquals(ReceiverStatus.Receiving, viewModel.uiState.value.status)
        assertEquals("photo.jpg", viewModel.uiState.value.fileName)
        assertEquals(500L, viewModel.uiState.value.bytesReceived)
        assertEquals(1000L, viewModel.uiState.value.totalBytes)
        assertEquals(0.5f, viewModel.uiState.value.progressFraction, 0.001f)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
