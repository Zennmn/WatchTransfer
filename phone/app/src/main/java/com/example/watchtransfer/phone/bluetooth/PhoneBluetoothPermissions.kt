package com.example.watchtransfer.phone.bluetooth

import android.Manifest
import android.os.Build

object PhoneBluetoothPermissions {
    fun requiredRuntimePermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }
}
