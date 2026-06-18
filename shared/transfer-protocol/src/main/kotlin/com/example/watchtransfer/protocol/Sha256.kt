package com.example.watchtransfer.protocol

import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    fun hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    /**
     * Converts already-computed digest bytes to lowercase hex.
     * Does NOT re-hash — use this when you've been calling digest.update() incrementally.
     */
    fun hexFromDigest(digest: MessageDigest): String {
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
