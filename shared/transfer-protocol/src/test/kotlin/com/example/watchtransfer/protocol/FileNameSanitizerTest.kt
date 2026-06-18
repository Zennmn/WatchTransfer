package com.example.watchtransfer.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {
    @Test
    fun removesPathSegmentsAndIllegalCharacters() {
        val result = FileNameSanitizer.sanitize("../Camera/DCIM/a:b?c*.jpg")

        assertEquals("a_b_c_.jpg", result)
    }

    @Test
    fun usesFallbackWhenNameIsBlankAfterCleanup() {
        val result = FileNameSanitizer.sanitize("////")

        assertEquals("received-file", result)
    }

    @Test
    fun capsVeryLongNamesWhileKeepingExtension() {
        val longName = "a".repeat(180) + ".txt"

        val result = FileNameSanitizer.sanitize(longName)

        assertEquals(120, result.length)
        assertEquals(true, result.endsWith(".txt"))
    }
}
