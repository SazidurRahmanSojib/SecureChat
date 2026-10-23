package com.example.securechat.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM: one encryption layer.
 *
 * Every call uses a fresh random 12-byte IV. The AAD ("associated data") is not
 * encrypted, but it is checked on decryption, so a message can't be moved to
 * another chat or relabelled with another sender without decryption failing.
 */
object MessageEncryption {

    const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

    fun encrypt(plain: ByteArray, key: SecretKey, aad: ByteArray): Sealed {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)

        return Sealed(iv, cipher.doFinal(plain))
    }

    /** Throws if the key is wrong or the data was changed. */
    fun decrypt(sealed: Sealed, key: SecretKey, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed.iv))
        cipher.updateAAD(aad)

        return cipher.doFinal(sealed.ciphertext)
    }
}
