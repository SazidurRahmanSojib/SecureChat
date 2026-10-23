package com.example.securechat.crypto

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** The two AES-256 keys for one chat. */
class ChatKeys(
    val chatId: String,
    layer1Key: ByteArray,
    layer2Key: ByteArray
) {
    internal val layer1: SecretKey = SecretKeySpec(layer1Key, "AES")
    internal val layer2: SecretKey = SecretKeySpec(layer2Key, "AES")
}

/**
 * Two-layer message encryption:
 *   plaintext → AES-GCM with the chat key (layer 1) → AES-GCM with the ECDH key (layer 2)
 * Decryption goes in reverse. Both layers are bound to the chat ID and sender ID.
 */
object ChatCrypto {

    class Encrypted(val iv: String, val ciphertext: String)

    fun encryptMessage(keys: ChatKeys, senderId: String, text: String): Encrypted {
        val inner = MessageEncryption.encrypt(
            text.toByteArray(Charsets.UTF_8),
            keys.layer1,
            aad("L1", keys.chatId, senderId)
        )

        // The inner IV travels inside the outer layer
        val outer = MessageEncryption.encrypt(
            inner.iv + inner.ciphertext,
            keys.layer2,
            aad("L2", keys.chatId, senderId)
        )

        return Encrypted(outer.iv.toBase64(), outer.ciphertext.toBase64())
    }

    /** Returns null if the message can't be decrypted (wrong key or changed data). */
    fun decryptMessage(keys: ChatKeys, senderId: String, iv: String, ciphertext: String): String? {
        return try {
            val innerBlob = MessageEncryption.decrypt(
                MessageEncryption.Sealed(iv.fromBase64(), ciphertext.fromBase64()),
                keys.layer2,
                aad("L2", keys.chatId, senderId)
            )
            if (innerBlob.size <= MessageEncryption.IV_BYTES) return null

            val inner = MessageEncryption.Sealed(
                innerBlob.copyOfRange(0, MessageEncryption.IV_BYTES),
                innerBlob.copyOfRange(MessageEncryption.IV_BYTES, innerBlob.size)
            )

            String(
                MessageEncryption.decrypt(inner, keys.layer1, aad("L1", keys.chatId, senderId)),
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            null
        }
    }

    /** The chat request message uses layer 2 only, so it can be read before the chat key is entered. */
    fun encryptRequest(layer2Key: ByteArray, chatId: String, requesterId: String, text: String): Encrypted {
        val sealed = MessageEncryption.encrypt(
            text.toByteArray(Charsets.UTF_8),
            SecretKeySpec(layer2Key, "AES"),
            aad("REQ", chatId, requesterId)
        )
        return Encrypted(sealed.iv.toBase64(), sealed.ciphertext.toBase64())
    }

    fun decryptRequest(
        layer2Key: ByteArray,
        chatId: String,
        requesterId: String,
        iv: String,
        ciphertext: String
    ): String? = try {
        String(
            MessageEncryption.decrypt(
                MessageEncryption.Sealed(iv.fromBase64(), ciphertext.fromBase64()),
                SecretKeySpec(layer2Key, "AES"),
                aad("REQ", chatId, requesterId)
            ),
            Charsets.UTF_8
        )
    } catch (e: Exception) {
        null
    }

    private fun aad(layer: String, chatId: String, senderId: String): ByteArray =
        "SecureChat|$layer|$chatId|$senderId".toByteArray(Charsets.UTF_8)
}
