package com.example.securechat.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps each chat's layer-1 key on this phone so the chat key only has to be
 * typed once per chat. The keys are encrypted with an AES key that lives in the
 * Android Keystore and never leaves it.
 */
class LocalKeyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("securechat_chat_keys", Context.MODE_PRIVATE)

    fun save(uid: String, chatId: String, layer1Key: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // The Keystore picks the IV itself
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val sealed = cipher.doFinal(layer1Key)

        prefs.edit()
            .putString(entryName(uid, chatId), cipher.iv.toBase64() + ":" + sealed.toBase64())
            .apply()
    }

    /** Returns null if this phone doesn't have the key for this chat yet. */
    fun load(uid: String, chatId: String): ByteArray? {
        val stored = prefs.getString(entryName(uid, chatId), null) ?: return null
        val parts = stored.split(":")
        if (parts.size != 2) return null

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, parts[0].fromBase64()))
            cipher.doFinal(parts[1].fromBase64())
        } catch (e: Exception) {
            // For example, restored from a backup onto a phone with a different Keystore.
            // Forget it; the user will be asked for the chat key again.
            remove(uid, chatId)
            null
        }
    }

    fun remove(uid: String, chatId: String) {
        prefs.edit().remove(entryName(uid, chatId)).apply()
    }

    private fun entryName(uid: String, chatId: String) = "$uid|$chatId"

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "securechat_local_chat_keys"
    }
}
