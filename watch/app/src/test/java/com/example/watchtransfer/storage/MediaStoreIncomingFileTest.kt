package com.example.watchtransfer.storage

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class MediaStoreIncomingFileTest {
    @Test
    fun commitThrowsWhenPendingFlagCannotBeCleared() {
        val operations = FakeMediaStoreFileOperations(commitResult = 0)
        val file = MediaStoreIncomingFile(
            operations = operations,
            uri = null,
            displayName = "file.txt"
        )

        assertThrows(IllegalStateException::class.java) {
            file.commit()
        }
    }

    private class FakeMediaStoreFileOperations(
        private val commitResult: Int
    ) : MediaStoreFileOperations {
        override fun openOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun commit(): Int = commitResult
        override fun delete(): Int = 1
    }
}
