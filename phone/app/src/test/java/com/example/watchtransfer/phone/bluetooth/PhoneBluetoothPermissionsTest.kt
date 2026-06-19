package com.example.watchtransfer.phone.bluetooth

import android.Manifest
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PhoneBluetoothPermissionsTest {
    @Test
    fun requiresConnectAndScanOnAndroid12AndAbove() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ),
            PhoneBluetoothPermissions.requiredRuntimePermissions(Build.VERSION_CODES.S)
        )
    }

    @Test
    fun doesNotRequestRuntimeBluetoothPermissionsBeforeAndroid12() {
        assertArrayEquals(
            emptyArray<String>(),
            PhoneBluetoothPermissions.requiredRuntimePermissions(Build.VERSION_CODES.R)
        )
    }
}
