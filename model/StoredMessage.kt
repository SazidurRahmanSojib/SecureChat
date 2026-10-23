package com.example.securechat.model

/** A message exactly as stored in Firestore, before decryption. */
data class StoredMessage(
    val id: String,
    val senderId: String,
    val createdAt: Long,
    val iv: String,
    val ciphertext: String,
    /** True for old plain-text messages from before encryption. */
    val isLegacy: Boolean,
    /** True while the message is still being uploaded from this phone. */
    val isPending: Boolean = false
)
