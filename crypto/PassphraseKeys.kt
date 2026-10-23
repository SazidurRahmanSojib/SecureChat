package com.example.securechat.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Layer 1: turns the chat key the two users agreed on in person into an AES key.
 *
 * PBKDF2-HMAC-SHA256 with 600,000 rounds makes every guess slow, so someone
 * who copies the encrypted messages can't try passphrases quickly.
 */
object PassphraseKeys {

    const val MIN_LENGTH = 12
    const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16

    class Derived(
        /** AES-256 key for the inner encryption layer. */
        val layer1Key: ByteArray,
        /** Stored in the chat document so the receiver can tell if they typed the right key. */
        val keyCheck: ByteArray
    )

    /**
     * Removes spaces at the start and end, and normalizes Unicode so the same
     * passphrase typed on different keyboards (including Bangla) gives the same key.
     */
    fun normalize(passphrase: String): String =
        Normalizer.normalize(passphrase.trim(), Normalizer.Form.NFC)

    fun isLongEnough(passphrase: String): Boolean =
        normalize(passphrase).length >= MIN_LENGTH

    fun newSalt(): ByteArray =
        ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /** Slow on purpose (around a second). Always call from a background thread. */
    fun derive(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = ITERATIONS
    ): Derived {
        val password = normalize(passphrase).toByteArray(Charsets.UTF_8)
        val master = pbkdf2Sha256(password, salt, iterations)
        password.fill(0)

        val derived = Derived(
            layer1Key = Hkdf.derive(master, salt, "SecureChat layer1 v1".toByteArray(Charsets.UTF_8)),
            keyCheck = Hkdf.derive(master, salt, "SecureChat key check v1".toByteArray(Charsets.UTF_8))
        )
        master.fill(0)
        return derived
    }

    fun checkMatches(derived: Derived, storedCheck: ByteArray): Boolean =
        MessageDigest.isEqual(derived.keyCheck, storedCheck)

    /**
     * PBKDF2-HMAC-SHA256, one 32-byte block.
     * Written out by hand because Android's built-in version needs API 26
     * and this app supports API 24.
     */
    internal fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        require(password.isNotEmpty()) { "Empty passphrase" }
        require(iterations >= 1) { "Invalid iteration count" }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))

        // U1 = HMAC(password, salt || INT(1))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        val u = mac.doFinal()
        val result = u.copyOf()

        // U2..Un, XORed together
        repeat(iterations - 1) {
            mac.update(u)
            mac.doFinal(u, 0)
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
            }
        }

        u.fill(0)
        return result
    }
}
