package com.example.watchtransfer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppNameTest {
    @Test
    fun usesChineseAppNameInLauncher() {
        assertTrue(
            File("src/main/AndroidManifest.xml").readText().contains("android:label=\"腕上传输\"")
        )
    }
}
