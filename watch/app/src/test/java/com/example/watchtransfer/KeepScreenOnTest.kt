package com.example.watchtransfer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeepScreenOnTest {
    @Test
    fun watchReceiverKeepsScreenOnWhileOpen() {
        val source = File("src/main/java/com/example/watchtransfer/MainActivity.kt").readText()

        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON"))
        assertTrue(source.contains("window.addFlags"))
    }
}
