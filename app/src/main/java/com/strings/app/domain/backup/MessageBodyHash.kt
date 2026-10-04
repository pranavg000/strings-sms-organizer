package com.strings.app.domain.backup

import java.security.MessageDigest

/**
 * Stable, device-independent fingerprint of a message body used as a backup matching key.
 * `Telephony.Sms._ID` is per-device, so on a new device the exported `deviceMessageId`
 * points at an unrelated SMS; (sender, bodyHash) identifies the same message anywhere.
 * One-way and truncated, so the bundle never carries message text.
 */
object MessageBodyHash {
    private const val HEX_LENGTH: Int = 16

    fun of(body: String): String {
        val digest: ByteArray = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }.take(HEX_LENGTH)
    }
}
