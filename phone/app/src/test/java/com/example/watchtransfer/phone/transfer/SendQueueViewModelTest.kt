package com.example.watchtransfer.phone.transfer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SendQueueViewModelTest {
    @Test
    fun sendsAllFilesAndRecordsSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sender = FakeQueueSender(
            listOf(
                SendFileResult.Success("Download/WatchTransfer/a.txt"),
                SendFileResult.Success("Download/WatchTransfer/b.txt")
            )
        )
        val viewModel = SendQueueViewModel(sender = sender, dispatcher = dispatcher)

        viewModel.setFiles(listOf(file("1", "a.txt"), file("2", "b.txt")))
        viewModel.startSending()
        advanceUntilIdle()

        assertEquals(QueueStatus.Success, viewModel.uiState.value.queueStatus)
        assertEquals("保存成功", viewModel.uiState.value.files[0].status)
        assertEquals("保存成功", viewModel.uiState.value.files[1].status)
    }

    @Test
    fun continuesAfterSingleFileFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sender = FakeQueueSender(
            listOf(
                SendFileResult.Failure("文件校验失败"),
                SendFileResult.Success("Download/WatchTransfer/b.txt")
            )
        )
        val viewModel = SendQueueViewModel(sender = sender, dispatcher = dispatcher)

        viewModel.setFiles(listOf(file("1", "bad.txt"), file("2", "b.txt")))
        viewModel.startSending()
        advanceUntilIdle()

        assertEquals(QueueStatus.PartialFailed, viewModel.uiState.value.queueStatus)
        assertEquals("文件校验失败", viewModel.uiState.value.files[0].status)
        assertEquals("保存成功", viewModel.uiState.value.files[1].status)
    }

    @Test
    fun recordsFailureWhenSenderThrows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sender = object : QueueFileSender {
            override suspend fun send(
                file: TransferFileSource,
                onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
            ): SendFileResult {
                throw RuntimeException("socket exploded")
            }
        }
        val viewModel = SendQueueViewModel(sender = sender, dispatcher = dispatcher)

        viewModel.setFiles(listOf(file("1", "a.txt")))
        viewModel.startSending()
        advanceUntilIdle()

        assertEquals(QueueStatus.Failed, viewModel.uiState.value.queueStatus)
        assertEquals("socket exploded", viewModel.uiState.value.files[0].status)
    }

    @Test
    fun removesQueuedFileBeforeSending() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sender = FakeQueueSender(emptyList())
        val viewModel = SendQueueViewModel(sender = sender, dispatcher = dispatcher)

        viewModel.setFiles(listOf(file("1", "a.txt"), file("2", "b.txt")))
        viewModel.removeFile("1")

        assertEquals(listOf("2"), viewModel.uiState.value.files.map { it.id })
        assertEquals(3L, viewModel.uiState.value.totalBytes)
        assertEquals(QueueStatus.Ready, viewModel.uiState.value.queueStatus)
    }

    @Test
    fun doesNotRemoveFileWhileSending() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sender = object : QueueFileSender {
            override suspend fun send(
                file: TransferFileSource,
                onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
            ): SendFileResult {
                awaitCancellation()
            }
        }
        val viewModel = SendQueueViewModel(sender = sender, dispatcher = dispatcher)

        viewModel.setFiles(listOf(file("1", "a.txt"), file("2", "b.txt")))
        viewModel.startSending()
        advanceUntilIdle()

        viewModel.removeFile("2")

        assertEquals(listOf("1", "2"), viewModel.uiState.value.files.map { it.id })
        viewModel.cancelSending()
        advanceUntilIdle()
    }

    @Test
    fun doesNotRestartUntilCancelledJobFinishesCleanup() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cleanupStarted = CompletableDeferred<Unit>()
        val cleanupRelease = CompletableDeferred<Unit>()
        var sendCalls = 0
        val sender = object : QueueFileSender {
            override suspend fun send(
                file: TransferFileSource,
                onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
            ): SendFileResult {
                sendCalls += 1
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        cleanupRelease.await()
                    }
                }
            }
        }
        val viewModel = SendQueueViewModel(sender = sender, dispatcher = dispatcher)

        viewModel.setFiles(listOf(file("1", "a.txt")))
        viewModel.startSending()
        advanceUntilIdle()
        viewModel.cancelSending()
        advanceUntilIdle()
        cleanupStarted.await()

        viewModel.startSending()
        advanceUntilIdle()
        assertEquals(1, sendCalls)

        cleanupRelease.complete(Unit)
        advanceUntilIdle()
        viewModel.startSending()
        advanceUntilIdle()
        assertEquals(2, sendCalls)
        viewModel.cancelSending()
        advanceUntilIdle()
    }

    private fun file(id: String, name: String): QueueFile {
        return QueueFile(
            id = id,
            source = object : TransferFileSource {
                override val displayName = name
                override val mimeType = "text/plain"
                override val sizeBytes = 3L
                override fun openInputStream(): java.io.InputStream = ByteArrayInputStream("abc".encodeToByteArray())
            }
        )
    }
}

private class FakeQueueSender(
    private val results: List<SendFileResult>
) : QueueFileSender {
    private var index = 0

    override suspend fun send(
        file: TransferFileSource,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
    ): SendFileResult {
        onProgress(file.sizeBytes, file.sizeBytes)
        return results[index++]
    }
}
