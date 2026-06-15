# Watch Receiver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the OPPO Watch X receiver APK that accepts one txt or image file over Bluetooth Classic while the app is open, verifies it, and saves it to `Download/WatchTransfer/`.

**Architecture:** The app is a single-module Kotlin Android project inside `watch/`. Bluetooth socket handling is separated from the pure file-transfer engine, so protocol parsing and transfer behavior can be unit-tested without a real watch. The UI is a small Compose screen driven by a `ViewModel` state flow.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose Material 3, AndroidX ViewModel, Kotlin coroutines, Bluetooth Classic RFCOMM, `MediaStore.Downloads`, JUnit, AndroidX instrumented tests.

---

## File Structure

- `watch/settings.gradle.kts`: Gradle project settings for the watch project.
- `watch/build.gradle.kts`: root plugin versions for the watch project.
- `watch/app/build.gradle.kts`: Android app module configuration and dependencies.
- `watch/app/src/main/AndroidManifest.xml`: app metadata, Bluetooth permissions, and activity declaration.
- `watch/app/src/main/java/com/example/watchtransfer/MainActivity.kt`: permission gate and Compose entry point.
- `watch/app/src/main/java/com/example/watchtransfer/protocol/FileNameSanitizer.kt`: safe file-name cleanup.
- `watch/app/src/main/java/com/example/watchtransfer/protocol/TransferHeader.kt`: validated file metadata model.
- `watch/app/src/main/java/com/example/watchtransfer/protocol/TransferProtocol.kt`: binary header parser.
- `watch/app/src/main/java/com/example/watchtransfer/receiver/IncomingFileStore.kt`: file-store abstraction for streaming content.
- `watch/app/src/main/java/com/example/watchtransfer/receiver/TransferSessionReceiver.kt`: pure transfer engine.
- `watch/app/src/main/java/com/example/watchtransfer/storage/DownloadFileStore.kt`: `MediaStore.Downloads` implementation.
- `watch/app/src/main/java/com/example/watchtransfer/bluetooth/BluetoothReceiveServer.kt`: RFCOMM server and Android Bluetooth wrapper.
- `watch/app/src/main/java/com/example/watchtransfer/ui/ReceiverUiState.kt`: screen state model.
- `watch/app/src/main/java/com/example/watchtransfer/ui/ReceiverViewModel.kt`: receive lifecycle and state ownership.
- `watch/app/src/main/java/com/example/watchtransfer/ui/WatchReceiverScreen.kt`: round-screen Compose UI.
- `watch/app/src/test/java/...`: pure JVM tests for protocol, transfer engine, Bluetooth wrapper seams, and ViewModel state.
- `watch/app/src/androidTest/java/...`: Android tests for `MediaStore` writing and Compose rendering.

## Device Compatibility Notes

**Target device:** OPPO Watch X 国行版 — runs ColorOS Watch 6.0, which is based on Android 13 (API 33), same foundation as Wear OS 4 but without Google Play Services.

- **Bluetooth RFCOMM:** Fully supported. Hardware supports SPP v1.1+. Standard `android.bluetooth` APIs work.
- **ADB sideloading:** Supported via charging cradle USB. This is the primary install method since there is no Play Store.
- **Storage:** `MediaStore.Downloads` is available on API 29+. Scoped storage is enforced on Android 13.
- **Permission quirk:** Wear OS may not show Bluetooth permission dialogs, but the APIs still work. If permission requests silently fail, add `@SuppressLint("MissingPermission")` as a fallback and re-check permission state on resume.

## Environment Notes

This machine has Java 21 available, but `gradle` is not currently on `PATH` and no Android SDK path is exported. Task 1 downloads a temporary Gradle distribution and generates `watch/gradlew.bat`; all later commands use that wrapper.

---

### Task 1: Android Project Scaffold

**Files:**
- Create: `watch/settings.gradle.kts`
- Create: `watch/.gitignore`
- Create: `watch/build.gradle.kts`
- Create: `watch/app/build.gradle.kts`
- Create: `watch/app/src/main/AndroidManifest.xml`
- Create: `watch/app/src/main/java/com/example/watchtransfer/MainActivity.kt`

- [x] **Step 1: Create Gradle settings**

Create `watch/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WatchTransferReceiver"
include(":app")
```

- [x] **Step 2: Create root build file**

Create `watch/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

- [x] **Step 3: Create app module build file**

Create `watch/app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.watchtransfer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.watchtransfer"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [x] **Step 4: Create manifest**

Create `watch/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />

    <uses-feature android:name="android.hardware.bluetooth" android:required="true" />

    <application
        android:allowBackup="false"
        android:label="Watch Transfer"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [x] **Step 5: Create minimal theme**

Create `watch/app/src/main/res/values/styles.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.NoActionBar">
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:navigationBarColor">#000000</item>
        <item name="android:statusBarColor">#000000</item>
    </style>
</resources>
```

- [x] **Step 6: Create watch project gitignore**

Create `watch/.gitignore`:

```gitignore
.gradle/
.gradle-local/
build/
local.properties
app/build/
```

- [x] **Step 7: Create placeholder activity**

Create `watch/app/src/main/java/com/example/watchtransfer/MainActivity.kt`:

```kotlin
package com.example.watchtransfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlaceholderApp()
        }
    }
}

@Composable
private fun PlaceholderApp() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "等待接收",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
```

- [x] **Step 8: Generate Gradle wrapper**

Run from `watch/`:

```powershell
New-Item -ItemType Directory -Force .gradle-local
Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -OutFile ".gradle-local/gradle-8.9-bin.zip"
Expand-Archive -Path ".gradle-local/gradle-8.9-bin.zip" -DestinationPath ".gradle-local" -Force
& ".gradle-local/gradle-8.9/bin/gradle.bat" wrapper --gradle-version 8.9 --distribution-type bin
```

Expected: `BUILD SUCCESSFUL` and these files exist:

```text
watch/gradlew
watch/gradlew.bat
watch/gradle/wrapper/gradle-wrapper.properties
watch/gradle/wrapper/gradle-wrapper.jar
```

- [x] **Step 9: Verify scaffold builds**

Run from `watch/`:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 10: Commit scaffold**

```powershell
git add watch/.gitignore watch/settings.gradle.kts watch/build.gradle.kts watch/app watch/gradlew watch/gradlew.bat watch/gradle/wrapper
git commit -m "chore: scaffold watch receiver app"
```

---

### Task 2: Transfer Protocol

**Files:**
- Create: `watch/app/src/test/java/com/example/watchtransfer/protocol/FileNameSanitizerTest.kt`
- Create: `watch/app/src/test/java/com/example/watchtransfer/protocol/TransferProtocolTest.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/protocol/FileNameSanitizer.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/protocol/TransferHeader.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/protocol/TransferProtocol.kt`

- [x] **Step 1: Write failing file-name sanitizer tests**

Create `watch/app/src/test/java/com/example/watchtransfer/protocol/FileNameSanitizerTest.kt`:

```kotlin
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
    fun capsVeryLongNames() {
        val longName = "a".repeat(180) + ".txt"

        val result = FileNameSanitizer.sanitize(longName)

        assertEquals(120, result.length)
        assertEquals(true, result.endsWith(".txt"))
    }
}
```

- [x] **Step 2: Run sanitizer tests to verify they fail**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FileNameSanitizerTest"
```

Expected: FAIL because `FileNameSanitizer` does not exist.

- [x] **Step 3: Implement file-name sanitizer**

Create `watch/app/src/main/java/com/example/watchtransfer/protocol/FileNameSanitizer.kt`:

```kotlin
package com.example.watchtransfer.protocol

object FileNameSanitizer {
    private const val MaxLength = 120
    private val illegalCharacters = Regex("""[\\/:*?"<>|]""")

    fun sanitize(rawName: String): String {
        val leafName = rawName
            .replace('\\', '/')
            .split('/')
            .lastOrNull()
            .orEmpty()
            .trim()

        val cleaned = leafName
            .replace(illegalCharacters, "_")
            .trim('.')
            .ifBlank { "received-file" }

        return trimPreservingExtension(cleaned)
    }

    private fun trimPreservingExtension(name: String): String {
        if (name.length <= MaxLength) return name

        val dotIndex = name.lastIndexOf('.')
        val extension = if (dotIndex in 1 until name.lastIndex) name.substring(dotIndex) else ""
        val baseLimit = MaxLength - extension.length
        val base = name.substring(0, baseLimit.coerceAtLeast(1))
        return base + extension
    }
}
```

- [x] **Step 4: Run sanitizer tests to verify they pass**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FileNameSanitizerTest"
```

Expected: PASS.

- [x] **Step 5: Write failing protocol parser tests**

Create `watch/app/src/test/java/com/example/watchtransfer/protocol/TransferProtocolTest.kt`:

```kotlin
package com.example.watchtransfer.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class TransferProtocolTest {
    private val sha = "a".repeat(64)

    @Test
    fun readsValidHeader() {
        val bytes = headerBytes(fileName = "note.txt", mimeType = "text/plain", size = 12L, sha256 = sha)

        val header = TransferProtocol().readHeader(ByteArrayInputStream(bytes))

        assertEquals(1, header.protocolVersion)
        assertEquals("note.txt", header.fileName)
        assertEquals("text/plain", header.mimeType)
        assertEquals(12L, header.fileSize)
        assertEquals(sha, header.sha256Hex)
    }

    @Test
    fun rejectsBadMagic() {
        val bytes = headerBytes(fileName = "note.txt", mimeType = "text/plain", size = 12L, sha256 = sha)
        bytes[0] = 'B'.code.toByte()

        assertThrows(TransferProtocolException::class.java) {
            TransferProtocol().readHeader(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun rejectsZeroSize() {
        val bytes = headerBytes(fileName = "empty.txt", mimeType = "text/plain", size = 0L, sha256 = sha)

        assertThrows(TransferProtocolException::class.java) {
            TransferProtocol().readHeader(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun rejectsInvalidSha() {
        val bytes = headerBytes(fileName = "note.txt", mimeType = "text/plain", size = 12L, sha256 = "z".repeat(64))

        assertThrows(TransferProtocolException::class.java) {
            TransferProtocol().readHeader(ByteArrayInputStream(bytes))
        }
    }

    private fun headerBytes(fileName: String, mimeType: String, size: Long, sha256: String): ByteArray {
        val nameBytes = fileName.encodeToByteArray()
        val mimeBytes = mimeType.encodeToByteArray()
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte()))
            data.writeByte(1)
            data.writeShort(nameBytes.size)
            data.writeShort(mimeBytes.size)
            data.writeLong(size)
            data.write(sha256.encodeToByteArray())
            data.write(nameBytes)
            data.write(mimeBytes)
        }
        return output.toByteArray()
    }
}
```

- [x] **Step 6: Run protocol tests to verify they fail**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransferProtocolTest"
```

Expected: FAIL because protocol classes do not exist.

- [x] **Step 7: Implement protocol model and parser**

Create `watch/app/src/main/java/com/example/watchtransfer/protocol/TransferHeader.kt`:

```kotlin
package com.example.watchtransfer.protocol

data class TransferHeader(
    val protocolVersion: Int,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val sha256Hex: String
)
```

Create `watch/app/src/main/java/com/example/watchtransfer/protocol/TransferProtocol.kt`:

```kotlin
package com.example.watchtransfer.protocol

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

class TransferProtocol {
    fun readHeader(input: InputStream): TransferHeader {
        val data = DataInputStream(input)
        try {
            val magic = ByteArray(4)
            data.readFully(magic)
            if (!magic.contentEquals(Magic)) {
                throw TransferProtocolException("协议标识不正确")
            }

            val version = data.readUnsignedByte()
            if (version != SupportedVersion) {
                throw TransferProtocolException("协议版本不支持")
            }

            val fileNameLength = data.readUnsignedShort()
            val mimeLength = data.readUnsignedShort()
            val fileSize = data.readLong()
            val shaBytes = ByteArray(Sha256HexLength)
            data.readFully(shaBytes)

            if (fileNameLength !in 1..MaxTextFieldBytes) {
                throw TransferProtocolException("文件名长度不合法")
            }
            if (mimeLength !in 1..MaxTextFieldBytes) {
                throw TransferProtocolException("文件类型长度不合法")
            }
            if (fileSize <= 0L) {
                throw TransferProtocolException("文件大小不合法")
            }

            val fileNameBytes = ByteArray(fileNameLength)
            val mimeBytes = ByteArray(mimeLength)
            data.readFully(fileNameBytes)
            data.readFully(mimeBytes)

            val sha256Hex = shaBytes.decodeToString().lowercase()
            if (!Sha256Regex.matches(sha256Hex)) {
                throw TransferProtocolException("校验值格式不合法")
            }

            return TransferHeader(
                protocolVersion = version,
                fileName = FileNameSanitizer.sanitize(fileNameBytes.decodeToString()),
                mimeType = mimeBytes.decodeToString().ifBlank { "application/octet-stream" },
                fileSize = fileSize,
                sha256Hex = sha256Hex
            )
        } catch (error: EOFException) {
            throw TransferProtocolException("文件头不完整", error)
        }
    }

    private companion object {
        val Magic = byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte())
        const val SupportedVersion = 1
        const val MaxTextFieldBytes = 512
        const val Sha256HexLength = 64
        val Sha256Regex = Regex("^[0-9a-f]{64}$")
    }
}

class TransferProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

- [x] **Step 8: Run all protocol tests**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*protocol*"
```

Expected: PASS.

- [x] **Step 9: Commit protocol**

```powershell
git add watch/app/src/main/java/com/example/watchtransfer/protocol watch/app/src/test/java/com/example/watchtransfer/protocol
git commit -m "feat: add watch transfer protocol parser"
```

---

### Task 3: Pure Transfer Engine

**Files:**
- Create: `watch/app/src/test/java/com/example/watchtransfer/receiver/TransferSessionReceiverTest.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/receiver/IncomingFileStore.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/receiver/TransferResult.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/receiver/TransferSessionReceiver.kt`

- [x] **Step 1: Write failing transfer engine tests**

Create `watch/app/src/test/java/com/example/watchtransfer/receiver/TransferSessionReceiverTest.kt`:

```kotlin
package com.example.watchtransfer.receiver

import com.example.watchtransfer.protocol.TransferHeader
import com.example.watchtransfer.protocol.TransferProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.MessageDigest

class TransferSessionReceiverTest {
    @Test
    fun commitsFileWhenChecksumMatches() {
        val content = "hello watch".encodeToByteArray()
        val store = FakeIncomingFileStore()
        val progress = mutableListOf<Long>()

        val result = TransferSessionReceiver().receive(
            input = ByteArrayInputStream(packetBytes("note.txt", "text/plain", content)),
            store = store,
            onProgress = { progress += it.bytesReceived }
        )

        assertEquals(TransferResult.Success("Download/WatchTransfer/note.txt", content.size.toLong()), result)
        assertArrayEquals(content, store.createdFile.bytes.toByteArray())
        assertTrue(store.createdFile.committed)
        assertEquals(false, store.createdFile.aborted)
        assertEquals(content.size.toLong(), progress.last())
    }

    @Test
    fun abortsFileWhenChecksumDoesNotMatch() {
        val content = "bad payload".encodeToByteArray()
        val store = FakeIncomingFileStore()
        val packet = packetBytes("bad.txt", "text/plain", content, shaOverride = "0".repeat(64))

        val result = TransferSessionReceiver().receive(
            input = ByteArrayInputStream(packet),
            store = store,
            onProgress = {}
        )

        assertTrue(result is TransferResult.Failure)
        assertEquals(false, store.createdFile.committed)
        assertEquals(true, store.createdFile.aborted)
    }

    @Test
    fun rejectsOversizedFileBeforeCreatingOutput() {
        val content = "large".encodeToByteArray()
        val store = FakeIncomingFileStore()
        val packet = packetBytes("large.jpg", "image/jpeg", content, declaredSize = 1024L)

        val result = TransferSessionReceiver(maxFileBytes = 10L).receive(
            input = ByteArrayInputStream(packet),
            store = store,
            onProgress = {}
        )

        assertTrue(result is TransferResult.Failure)
        assertEquals(0, store.createCount)
    }

    private fun packetBytes(
        fileName: String,
        mimeType: String,
        content: ByteArray,
        declaredSize: Long = content.size.toLong(),
        shaOverride: String? = null
    ): ByteArray {
        val sha = shaOverride ?: content.sha256Hex()
        val nameBytes = fileName.encodeToByteArray()
        val mimeBytes = mimeType.encodeToByteArray()
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte()))
            data.writeByte(1)
            data.writeShort(nameBytes.size)
            data.writeShort(mimeBytes.size)
            data.writeLong(declaredSize)
            data.write(sha.encodeToByteArray())
            data.write(nameBytes)
            data.write(mimeBytes)
            data.write(content)
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private class FakeIncomingFileStore : IncomingFileStore {
        var createCount = 0
        lateinit var createdFile: FakeIncomingFile

        override fun create(header: TransferHeader): IncomingFile {
            createCount += 1
            createdFile = FakeIncomingFile("Download/WatchTransfer/${header.fileName}")
            return createdFile
        }
    }

    private class FakeIncomingFile(private val path: String) : IncomingFile {
        val bytes = ByteArrayOutputStream()
        var committed = false
        var aborted = false

        override val displayPath: String
            get() = path

        override fun openOutputStream(): OutputStream = bytes

        override fun commit() {
            committed = true
        }

        override fun abort() {
            aborted = true
        }

    }
}
```

- [x] **Step 2: Run transfer engine tests to verify they fail**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransferSessionReceiverTest"
```

Expected: FAIL because receiver classes do not exist.

- [x] **Step 3: Implement transfer engine contracts**

Create `watch/app/src/main/java/com/example/watchtransfer/receiver/IncomingFileStore.kt`:

```kotlin
package com.example.watchtransfer.receiver

import com.example.watchtransfer.protocol.TransferHeader
import java.io.OutputStream

interface IncomingFileStore {
    fun create(header: TransferHeader): IncomingFile
}

interface IncomingFile {
    val displayPath: String
    fun openOutputStream(): OutputStream
    fun commit()
    fun abort()
}
```

Create `watch/app/src/main/java/com/example/watchtransfer/receiver/TransferResult.kt`:

```kotlin
package com.example.watchtransfer.receiver

data class TransferProgress(
    val fileName: String,
    val bytesReceived: Long,
    val totalBytes: Long
)

sealed interface TransferResult {
    data class Success(val displayPath: String, val bytesWritten: Long) : TransferResult
    data class Failure(val message: String) : TransferResult
}
```

- [x] **Step 4: Implement transfer engine**

Create `watch/app/src/main/java/com/example/watchtransfer/receiver/TransferSessionReceiver.kt`:

```kotlin
package com.example.watchtransfer.receiver

import com.example.watchtransfer.protocol.TransferProtocol
import java.security.MessageDigest

class TransferSessionReceiver(
    private val protocol: TransferProtocol = TransferProtocol(),
    private val maxFileBytes: Long = 30L * 1024L * 1024L,
    private val bufferSize: Int = 8 * 1024
) {
    fun receive(
        input: java.io.InputStream,
        store: IncomingFileStore,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult {
        var incomingFile: IncomingFile? = null
        return try {
            val header = protocol.readHeader(input)
            if (header.fileSize > maxFileBytes) {
                return TransferResult.Failure("文件超过最大限制")
            }

            val target = store.create(header)
            incomingFile = target
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(bufferSize)
            var remaining = header.fileSize
            var received = 0L

            target.openOutputStream().use { output ->
                while (remaining > 0L) {
                    val readSize = minOf(buffer.size.toLong(), remaining).toInt()
                    val count = input.read(buffer, 0, readSize)
                    if (count == -1) {
                        throw IllegalStateException("连接已断开")
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    received += count
                    remaining -= count
                    onProgress(
                        TransferProgress(
                            fileName = header.fileName,
                            bytesReceived = received,
                            totalBytes = header.fileSize
                        )
                    )
                }
            }

            val actualSha = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (actualSha != header.sha256Hex) {
                target.abort()
                TransferResult.Failure("文件校验失败")
            } else {
                target.commit()
                TransferResult.Success(target.displayPath, received)
            }
        } catch (error: Exception) {
            incomingFile?.abort()
            TransferResult.Failure(error.message ?: "接收失败")
        }
    }
}
```

- [x] **Step 5: Run transfer engine tests**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransferSessionReceiverTest"
```

Expected: PASS.

- [x] **Step 6: Commit transfer engine**

```powershell
git add watch/app/src/main/java/com/example/watchtransfer/receiver watch/app/src/test/java/com/example/watchtransfer/receiver
git commit -m "feat: add watch transfer session receiver"
```

---

### Task 4: Download Folder Storage

**Files:**
- Create: `watch/app/src/main/java/com/example/watchtransfer/storage/DownloadFileStore.kt`
- Create: `watch/app/src/androidTest/java/com/example/watchtransfer/storage/DownloadFileStoreTest.kt`

- [x] **Step 1: Write failing instrumented storage test**

Create `watch/app/src/androidTest/java/com/example/watchtransfer/storage/DownloadFileStoreTest.kt`:

```kotlin
package com.example.watchtransfer.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.watchtransfer.protocol.TransferHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadFileStoreTest {
    @Test
    fun writesCommittedFileToWatchTransferDownloadFolder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DownloadFileStore(context.contentResolver)
        val header = TransferHeader(
            protocolVersion = 1,
            fileName = "instrumented-note.txt",
            mimeType = "text/plain",
            fileSize = 5L,
            sha256Hex = "a".repeat(64)
        )

        val file = store.create(header) as MediaStoreIncomingFile
        file.openOutputStream().use { output ->
            output.write("hello".encodeToByteArray())
        }
        file.commit()

        assertEquals("Download/WatchTransfer/instrumented-note.txt", file.displayPath)
        assertTrue(file.uri.toString().isNotBlank())

        context.contentResolver.delete(file.uri, null, null)
    }
}
```

- [x] **Step 2: Run storage test to verify it fails**

Run from `watch/` with a connected Android device or emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*DownloadFileStoreTest"
```

Expected: FAIL because `DownloadFileStore` does not exist.

- [x] **Step 3: Implement `MediaStore.Downloads` file store**

Create `watch/app/src/main/java/com/example/watchtransfer/storage/DownloadFileStore.kt`:

```kotlin
package com.example.watchtransfer.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.watchtransfer.protocol.TransferHeader
import com.example.watchtransfer.receiver.IncomingFile
import com.example.watchtransfer.receiver.IncomingFileStore
import java.io.OutputStream

class DownloadFileStore(
    private val contentResolver: ContentResolver
) : IncomingFileStore {
    override fun create(header: TransferHeader): IncomingFile {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, header.fileName)
            put(MediaStore.Downloads.MIME_TYPE, header.mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/WatchTransfer")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建下载文件")

        return MediaStoreIncomingFile(
            contentResolver = contentResolver,
            uri = uri,
            displayName = header.fileName
        )
    }
}

class MediaStoreIncomingFile(
    private val contentResolver: ContentResolver,
    val uri: Uri,
    private val displayName: String
) : IncomingFile {
    override val displayPath: String = "Download/WatchTransfer/$displayName"

    override fun openOutputStream(): OutputStream {
        return contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法写入下载文件")
    }

    override fun commit() {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        contentResolver.update(uri, values, null, null)
    }

    override fun abort() {
        contentResolver.delete(uri, null, null)
    }
}
```

- [x] **Step 4: Run storage instrumented test**

Run from `watch/`:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*DownloadFileStoreTest"
```

Expected: PASS on a connected device or emulator with public downloads support.

- [x] **Step 5: Commit storage**

```powershell
git add watch/app/src/main/java/com/example/watchtransfer/storage watch/app/src/androidTest/java/com/example/watchtransfer/storage
git commit -m "feat: save received files to downloads"
```

---

### Task 5: Bluetooth RFCOMM Server

**Files:**
- Create: `watch/app/src/test/java/com/example/watchtransfer/bluetooth/BluetoothReceiveServerTest.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/bluetooth/BluetoothReceiveServer.kt`

- [x] **Step 1: Write failing Bluetooth server seam test**

Create `watch/app/src/test/java/com/example/watchtransfer/bluetooth/BluetoothReceiveServerTest.kt`:

```kotlin
package com.example.watchtransfer.bluetooth

import com.example.watchtransfer.receiver.IncomingFileStore
import com.example.watchtransfer.receiver.TransferResult
import com.example.watchtransfer.receiver.TransferSessionReceiver
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class BluetoothReceiveServerTest {
    @Test
    fun emitsWaitingConnectedAndResultEvents() = runTest {
        val socket = FakeConnectedSocket("Pixel Phone")
        val factory = FakeRfcommSocketFactory(socket)
        val sessionReceiver = object : SessionReceiver {
            override fun receive(
                input: java.io.InputStream,
                store: IncomingFileStore,
                onProgress: (com.example.watchtransfer.receiver.TransferProgress) -> Unit
            ): TransferResult {
                return TransferResult.Success("Download/WatchTransfer/a.txt", 3L)
            }
        }
        val server = BluetoothReceiveServer(factory, sessionReceiver, FakeIncomingFileStore())

        val events = server.receiveOnce().toList()

        assertEquals(BluetoothReceiveEvent.Waiting, events[0])
        assertEquals(BluetoothReceiveEvent.Connected("Pixel Phone"), events[1])
        assertEquals(BluetoothReceiveEvent.Completed("Download/WatchTransfer/a.txt", 3L), events[2])
        assertEquals(true, socket.closed)
    }

    private class FakeRfcommSocketFactory(
        private val socket: FakeConnectedSocket
    ) : RfcommSocketFactory {
        override fun openServerSocket(): RfcommServerSocket {
            return object : RfcommServerSocket {
                override fun accept(): ConnectedSocket = socket
                override fun close() = Unit
            }
        }
    }

    private class FakeConnectedSocket(
        override val remoteName: String
    ) : ConnectedSocket {
        var closed = false
        override fun inputStream(): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
        override fun close() {
            closed = true
        }
    }

    private class FakeIncomingFileStore : IncomingFileStore {
        override fun create(header: com.example.watchtransfer.protocol.TransferHeader): com.example.watchtransfer.receiver.IncomingFile {
            throw AssertionError("store is not used by fake receiver")
        }
    }
}
```

- [x] **Step 2: Run Bluetooth tests to verify they fail**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*BluetoothReceiveServerTest"
```

Expected: FAIL because Bluetooth server classes do not exist.

- [x] **Step 3: Implement Bluetooth server seam and Android wrapper**

Create `watch/app/src/main/java/com/example/watchtransfer/bluetooth/BluetoothReceiveServer.kt`:

```kotlin
package com.example.watchtransfer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.example.watchtransfer.receiver.IncomingFileStore
import com.example.watchtransfer.receiver.TransferProgress
import com.example.watchtransfer.receiver.TransferResult
import com.example.watchtransfer.receiver.TransferSessionReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.util.UUID

val WatchTransferServiceUuid: UUID = UUID.fromString("8d520b7a-9f29-4ce1-8a8e-f3a1e2f7b921")

sealed interface BluetoothReceiveEvent {
    data object Waiting : BluetoothReceiveEvent
    data class Connected(val remoteName: String) : BluetoothReceiveEvent
    data class Progress(val progress: TransferProgress) : BluetoothReceiveEvent
    data class Completed(val displayPath: String, val bytesWritten: Long) : BluetoothReceiveEvent
    data class Failed(val message: String) : BluetoothReceiveEvent
}

interface SessionReceiver {
    fun receive(
        input: InputStream,
        store: IncomingFileStore,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult
}

class DefaultSessionReceiver(
    private val delegate: TransferSessionReceiver = TransferSessionReceiver()
) : SessionReceiver {
    override fun receive(
        input: InputStream,
        store: IncomingFileStore,
        onProgress: (TransferProgress) -> Unit
    ): TransferResult = delegate.receive(input, store, onProgress)
}

interface RfcommSocketFactory {
    fun openServerSocket(): RfcommServerSocket
}

interface RfcommServerSocket {
    fun accept(): ConnectedSocket
    fun close()
}

interface ConnectedSocket {
    val remoteName: String
    fun inputStream(): InputStream
    fun close()
}

class BluetoothReceiveServer(
    private val socketFactory: RfcommSocketFactory,
    private val sessionReceiver: SessionReceiver,
    private val store: IncomingFileStore
) {
    fun receiveOnce(): Flow<BluetoothReceiveEvent> = channelFlow {
        send(BluetoothReceiveEvent.Waiting)
        val serverSocket = socketFactory.openServerSocket()
        try {
            val socket = serverSocket.accept()
            try {
                send(BluetoothReceiveEvent.Connected(socket.remoteName))
                val result = sessionReceiver.receive(socket.inputStream(), store) { progress ->
                    trySend(BluetoothReceiveEvent.Progress(progress))
                }
                when (result) {
                    is TransferResult.Success -> send(
                        BluetoothReceiveEvent.Completed(result.displayPath, result.bytesWritten)
                    )
                    is TransferResult.Failure -> send(BluetoothReceiveEvent.Failed(result.message))
                }
            } finally {
                socket.close()
            }
        } catch (error: Exception) {
            send(BluetoothReceiveEvent.Failed(error.message ?: "蓝牙接收失败"))
        } finally {
            serverSocket.close()
            close()
        }
    }.flowOn(Dispatchers.IO)
}

class AndroidRfcommSocketFactory(
    private val bluetoothAdapter: BluetoothAdapter
) : RfcommSocketFactory {
    @SuppressLint("MissingPermission")
    override fun openServerSocket(): RfcommServerSocket {
        val socket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
            "WatchTransferReceiver",
            WatchTransferServiceUuid
        )
        return AndroidRfcommServerSocket(socket)
    }
}

private class AndroidRfcommServerSocket(
    private val delegate: BluetoothServerSocket
) : RfcommServerSocket {
    @SuppressLint("MissingPermission")
    override fun accept(): ConnectedSocket = AndroidConnectedSocket(delegate.accept())
    override fun close() = delegate.close()
}

private class AndroidConnectedSocket(
    private val delegate: BluetoothSocket
) : ConnectedSocket {
    override val remoteName: String
        @SuppressLint("MissingPermission")
        get() = delegate.remoteDevice?.name ?: "Android 手机"

    override fun inputStream(): InputStream = delegate.inputStream
    override fun close() = delegate.close()
}
```

- [x] **Step 4: Run Bluetooth tests**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*BluetoothReceiveServerTest"
```

Expected: PASS.

- [x] **Step 5: Commit Bluetooth server**

```powershell
git add watch/app/src/main/java/com/example/watchtransfer/bluetooth watch/app/src/test/java/com/example/watchtransfer/bluetooth
git commit -m "feat: add bluetooth receive server"
```

---

### Task 6: Receiver ViewModel State

**Files:**
- Create: `watch/app/src/test/java/com/example/watchtransfer/ui/ReceiverViewModelTest.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/ui/ReceiverUiState.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/ui/ReceiverViewModel.kt`

- [x] **Step 1: Write failing ViewModel state tests**

Create `watch/app/src/test/java/com/example/watchtransfer/ui/ReceiverViewModelTest.kt`:

```kotlin
package com.example.watchtransfer.ui

import com.example.watchtransfer.bluetooth.BluetoothReceiveEvent
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
```

- [x] **Step 2: Run ViewModel tests to verify they fail**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ReceiverViewModelTest"
```

Expected: FAIL because UI state classes do not exist.

- [x] **Step 3: Implement UI state model**

Create `watch/app/src/main/java/com/example/watchtransfer/ui/ReceiverUiState.kt`:

```kotlin
package com.example.watchtransfer.ui

enum class ReceiverStatus {
    NeedsPermission,
    BluetoothOff,
    Idle,
    Waiting,
    Connected,
    Receiving,
    Success,
    Failed
}

data class ReceiverUiState(
    val status: ReceiverStatus = ReceiverStatus.Idle,
    val remoteName: String = "",
    val fileName: String = "",
    val bytesReceived: Long = 0L,
    val totalBytes: Long = 0L,
    val savedPath: String = "",
    val successCount: Int = 0,
    val message: String = ""
) {
    val progressFraction: Float
        get() = if (totalBytes <= 0L) 0f else (bytesReceived.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}
```

- [x] **Step 4: Implement ViewModel**

Create `watch/app/src/main/java/com/example/watchtransfer/ui/ReceiverViewModel.kt`:

```kotlin
package com.example.watchtransfer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchtransfer.bluetooth.BluetoothReceiveEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReceiverViewModel(
    private val receiveOnce: () -> Flow<BluetoothReceiveEvent>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState: StateFlow<ReceiverUiState> = _uiState.asStateFlow()

    private var receiveJob: Job? = null

    fun startReceiving() {
        if (receiveJob?.isActive == true) return

        receiveJob = viewModelScope.launch(dispatcher) {
            receiveOnce().collect { event ->
                reduce(event)
            }
        }
    }

    fun stopReceiving() {
        receiveJob?.cancel()
        receiveJob = null
        _uiState.value = _uiState.value.copy(status = ReceiverStatus.Idle, message = "")
    }

    private fun reduce(event: BluetoothReceiveEvent) {
        val current = _uiState.value
        _uiState.value = when (event) {
            BluetoothReceiveEvent.Waiting -> current.copy(
                status = ReceiverStatus.Waiting,
                message = "等待手机连接"
            )
            is BluetoothReceiveEvent.Connected -> current.copy(
                status = ReceiverStatus.Connected,
                remoteName = event.remoteName,
                message = "已连接"
            )
            is BluetoothReceiveEvent.Progress -> current.copy(
                status = ReceiverStatus.Receiving,
                fileName = event.progress.fileName,
                bytesReceived = event.progress.bytesReceived,
                totalBytes = event.progress.totalBytes,
                message = "正在接收"
            )
            is BluetoothReceiveEvent.Completed -> current.copy(
                status = ReceiverStatus.Success,
                savedPath = event.displayPath,
                successCount = current.successCount + 1,
                message = "保存成功"
            )
            is BluetoothReceiveEvent.Failed -> current.copy(
                status = ReceiverStatus.Failed,
                message = event.message
            )
        }
    }
}
```

- [x] **Step 5: Run ViewModel tests**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ReceiverViewModelTest"
```

Expected: PASS.

- [x] **Step 6: Commit ViewModel**

```powershell
git add watch/app/src/main/java/com/example/watchtransfer/ui watch/app/src/test/java/com/example/watchtransfer/ui
git commit -m "feat: add receiver view model state"
```

---

### Task 7: Round Watch UI and Permission Gate

**Files:**
- Create: `watch/app/src/androidTest/java/com/example/watchtransfer/ui/WatchReceiverScreenTest.kt`
- Create: `watch/app/src/main/java/com/example/watchtransfer/ui/WatchReceiverScreen.kt`
- Modify: `watch/app/src/main/java/com/example/watchtransfer/MainActivity.kt`

- [x] **Step 1: Write failing Compose UI test**

Create `watch/app/src/androidTest/java/com/example/watchtransfer/ui/WatchReceiverScreenTest.kt`:

```kotlin
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
```

- [x] **Step 2: Run Compose UI test to verify it fails**

Run from `watch/`:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*WatchReceiverScreenTest"
```

Expected: FAIL because `WatchReceiverScreen` does not exist.

- [x] **Step 3: Implement round-screen Compose UI**

Create `watch/app/src/main/java/com/example/watchtransfer/ui/WatchReceiverScreen.kt`:

```kotlin
package com.example.watchtransfer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WatchReceiverScreen(
    state: ReceiverUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(34.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProgressRing(state.progressFraction)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = titleFor(state),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detailFor(state),
                color = Color(0xFFB8C0CC),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (state.status == ReceiverStatus.Failed || state.status == ReceiverStatus.Idle) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Text("开始接收")
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(progress: Float) {
    Canvas(modifier = Modifier.size(86.dp)) {
        val strokeWidth = 8.dp.toPx()
        drawArc(
            color = Color(0xFF263142),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            size = Size(size.width, size.height),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF4CAF50),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            size = Size(size.width, size.height),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

private fun titleFor(state: ReceiverUiState): String = when (state.status) {
    ReceiverStatus.NeedsPermission -> "需要蓝牙权限"
    ReceiverStatus.BluetoothOff -> "蓝牙未开启"
    ReceiverStatus.Idle -> "准备接收"
    ReceiverStatus.Waiting -> "等待手机连接"
    ReceiverStatus.Connected -> "已连接"
    ReceiverStatus.Receiving -> state.fileName.ifBlank { "正在接收" }
    ReceiverStatus.Success -> "保存成功"
    ReceiverStatus.Failed -> "接收失败"
}

private fun detailFor(state: ReceiverUiState): String = when (state.status) {
    ReceiverStatus.Success -> state.savedPath
    ReceiverStatus.Receiving -> "${state.bytesReceived / 1024} KB / ${state.totalBytes / 1024} KB"
    ReceiverStatus.Connected -> state.remoteName
    else -> state.message
}
```

- [x] **Step 4: Replace placeholder activity with permission and receiver wiring**

Modify `watch/app/src/main/java/com/example/watchtransfer/MainActivity.kt`:

```kotlin
package com.example.watchtransfer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.watchtransfer.bluetooth.AndroidRfcommSocketFactory
import com.example.watchtransfer.bluetooth.BluetoothReceiveServer
import com.example.watchtransfer.bluetooth.DefaultSessionReceiver
import com.example.watchtransfer.storage.DownloadFileStore
import com.example.watchtransfer.ui.ReceiverStatus
import com.example.watchtransfer.ui.ReceiverUiState
import com.example.watchtransfer.ui.ReceiverViewModel
import com.example.watchtransfer.ui.WatchReceiverScreen
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = requiredBluetoothPermissions()
        if (!hasPermissions(permissions)) {
            permissionLauncher.launch(permissions)
        }

        setContent {
            val bluetoothAdapter = bluetoothAdapter()
            if (!hasPermissions(permissions)) {
                WatchReceiverScreen(
                    state = ReceiverUiState(
                        status = ReceiverStatus.NeedsPermission,
                        message = "请授权蓝牙权限"
                    ),
                    onRetry = { permissionLauncher.launch(permissions) }
                )
            } else if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                WatchReceiverScreen(
                    state = ReceiverUiState(
                        status = ReceiverStatus.BluetoothOff,
                        message = "请先打开手表蓝牙"
                    ),
                    onRetry = {}
                )
            } else {
                val factory = remember {
                    val server = BluetoothReceiveServer(
                        socketFactory = AndroidRfcommSocketFactory(bluetoothAdapter),
                        sessionReceiver = DefaultSessionReceiver(),
                        store = DownloadFileStore(contentResolver)
                    )
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ReceiverViewModel(receiveOnce = { server.receiveOnce() }) as T
                        }
                    }
                }
                val viewModel: ReceiverViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(Unit) {
                    viewModel.startReceiving()
                }
                WatchReceiverScreen(
                    state = state,
                    onRetry = { viewModel.startReceiving() }
                )
            }
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
```

- [x] **Step 5: Build activity and UI wiring**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: PASS.

- [x] **Step 6: Run UI tests**

Run from `watch/`:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --tests "*WatchReceiverScreenTest"
```

Expected: PASS.

- [x] **Step 7: Commit UI and wiring**

```powershell
git add watch/app/src/main/java/com/example/watchtransfer/MainActivity.kt watch/app/src/main/java/com/example/watchtransfer/ui watch/app/src/androidTest/java/com/example/watchtransfer/ui watch/app/build.gradle.kts
git commit -m "feat: add watch receiver ui"
```

---

### Task 8: Build, Install, and Manual Transfer Verification

**Files:**
- Modify only if verification exposes a concrete failing test or compile error.

- [x] **Step 1: Run JVM test suite**

Run from `watch/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Run Android instrumented tests**

Run from `watch/` with a connected Android device or OPPO Watch X:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Build debug APK**

Run from `watch/`:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected APK:

```text
watch/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 4: Install on OPPO Watch X** (manual - requires device)

Run:

```powershell
adb devices
adb install -r watch/app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`.

- [ ] **Step 5: Verify receiver screen** (manual - requires device)

Open `Watch Transfer` on the watch.

Expected:

- If permissions are missing, the app asks for Bluetooth permissions.
- If Bluetooth is off, the app shows `蓝牙未开启`.
- If Bluetooth is on, the app shows `等待手机连接`.

- [ ] **Step 6: Verify transfer with a temporary sender** (manual - requires device)

Before the phone app exists, use a small Android sender app or an `adb`-installed test utility that connects to UUID `8d520b7a-9f29-4ce1-8a8e-f3a1e2f7b921` and writes the protocol packet:

```text
magic: WTRF
version: 1 byte, value 1
fileNameLength: unsigned short, big endian
mimeTypeLength: unsigned short, big endian
fileSize: signed long, big endian
sha256Hex: 64 ASCII bytes
fileName: UTF-8 bytes
mimeType: UTF-8 bytes
body: file bytes
```

Expected on watch:

- During transfer, progress ring advances.
- On success, screen shows `保存成功`.
- Saved path is `Download/WatchTransfer/<file-name>`.

- [ ] **Step 7: Verify failed checksum cleanup** (manual - requires device)

Send the same packet with an intentionally wrong `sha256Hex`.

Expected:

- Screen shows `接收失败`.
- No committed file remains in `Download/WatchTransfer/`.

- [ ] **Step 8: Final status and commit**

Run:

```powershell
git status --short
```

Expected: only intended files are changed.

Commit final verification fixes if any:

```powershell
git add watch
git commit -m "test: verify watch receiver on device"
```
