package com.example.securechat.model

/** A decrypted message, ready to show. */
data class ChatMessage(
    val id: String,
    val senderId: String,
    /** Null if the message couldn't be decrypted. */
    val text: String?,
    val createdAt: Long,
    /** True while the message is still being uploaded from this phone. */
    val isPending: Boolean = false
)
