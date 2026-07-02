package com.example.watchtransfer.phone

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppNameTest {
    @Test
    fun usesChineseAppNameInLauncherAndHeader() {
        assertTrue(
            File("src/main/AndroidManifest.xml").readText().contains("android:label=\"腕上传输\"")
        )
        assertTrue(
            File("src/main/java/com/example/watchtransfer/phone/ui/PhoneSenderScreen.kt").readText().contains("Text(\"腕上传输\")")
        )
    }
}
