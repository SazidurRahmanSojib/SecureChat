package com.example.securechat.model

/** The chats/{chatId} document: the chat request and, once accepted, the chat itself. */
data class ChatInfo(
    val chatId: String,
    val requesterId: String,
    val recipientId: String,
    val status: String,
    /** The request message, encrypted with layer 2 only. */
    val requestIv: String,
    val requestCiphertext: String,
    /** Salt for turning the chat key into an AES key. */
    val kdfSalt: String,
    /** Lets the receiver's phone check the chat key without the key ever being stored. */
    val keyCheck: String,
    /** Both public keys are saved when the request is made, and used for the whole chat. */
    val requesterPublicKey: String,
    val recipientPublicKey: String,
    val createdAt: Long? = null,
    val acceptedAt: Long? = null,
    /** Time of the newest message (never its content). */
    val lastMessageAt: Long? = null,
    val lastSenderId: String? = null,
    /** Unread message count per user ID. */
    val unread: Map<String, Long> = emptyMap(),
    /** When each user last opened the chat; used for read ticks. */
    val readAt: Map<String, Long> = emptyMap()
) {
    val isPending: Boolean get() = status == STATUS_PENDING
    val isAccepted: Boolean get() = status == STATUS_ACCEPTED

    /** Used to sort the chat list, newest first. */
    val lastActivity: Long get() = lastMessageAt ?: acceptedAt ?: createdAt ?: 0L

    fun otherUid(myUid: String): String =
        if (myUid == requesterId) recipientId else requesterId

    fun myPublicKey(myUid: String): String =
        if (myUid == requesterId) requesterPublicKey else recipientPublicKey

    fun otherPublicKey(myUid: String): String =
        if (myUid == requesterId) recipientPublicKey else requesterPublicKey

    fun unreadFor(uid: String): Long = unread[uid] ?: 0L

    fun readAtFor(uid: String): Long? = readAt[uid]

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
    }
}
