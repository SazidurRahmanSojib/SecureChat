package com.example.securechat.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF with HMAC-SHA256 (RFC 5869).
 * Turns a secret into one or more independent keys, each labelled by `info`.
 */
object Hkdf {

    private const val HASH_BYTES = 32

    fun derive(
        secret: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int = 32
    ): ByteArray {
        require(length in 1..255 * HASH_BYTES) { "Invalid HKDF length" }

        val mac = Mac.getInstance("HmacSHA256")

        // Extract
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(HASH_BYTES) else salt, "HmacSHA256"))
        val prk = mac.doFinal(secret)

        // Expand
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var position = 0
        var counter = 1

        while (position < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()

            val count = minOf(previous.size, length - position)
            System.arraycopy(previous, 0, output, position, count)
            position += count
            counter++
        }

        prk.fill(0)
        return output
    }
}
