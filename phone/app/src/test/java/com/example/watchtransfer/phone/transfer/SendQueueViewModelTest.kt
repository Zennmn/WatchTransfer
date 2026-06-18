package com.example.watchtransfer.phone.transfer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
