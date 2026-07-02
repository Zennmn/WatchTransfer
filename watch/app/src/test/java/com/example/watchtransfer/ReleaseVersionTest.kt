package com.example.watchtransfer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseVersionTest {
    @Test
    fun watchReleaseVersionIs011() {
        val buildFile = File("build.gradle.kts").readText()

        assertTrue(buildFile.contains("versionCode = 2"))
        assertTrue(buildFile.contains("versionName = \"0.1.1\""))
    }
}
