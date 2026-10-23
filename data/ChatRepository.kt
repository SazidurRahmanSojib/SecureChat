package com.example.securechat.data

import com.example.securechat.model.ChatInfo
import com.example.securechat.model.ChatUser
import com.example.securechat.model.StoredMessage
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

class ChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /** Same ID on both devices: the two UIDs, sorted and joined by "_". */
    fun chatIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    // ---------- Users ----------

    /** Live list of every registered user, including the signed-in user. */
    fun listenToUsers(
        onResult: (Result<List<ChatUser>>) -> Unit
    ): ListenerRegistration =
        db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onResult(Result.failure(error))
            } else if (snapshot != null) {
                onResult(Result.success(snapshot.documents.mapNotNull { it.toChatUser() }))
            }
        }

    suspend fun getUser(uid: String): ChatUser? =
        db.collection("users").document(uid).get().awaitTask().toChatUser()

    // ---------- Blocking ----------

    /** Live set of user IDs this user has blocked. */
    fun listenToBlocked(
        myUid: String,
        onResult: (Result<Set<String>>) -> Unit
    ): ListenerRegistration =
        blockedOf(myUid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onResult(Result.failure(error))
            } else if (snapshot != null) {
                onResult(Result.success(snapshot.documents.map { it.id }.toSet()))
            }
        }

    suspend fun blockUser(myUid: String, otherUid: String) {
        blockedOf(myUid).document(otherUid)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp()))
            .awaitTask()
    }

    suspend fun unblockUser(myUid: String, otherUid: String) {
        blockedOf(myUid).document(otherUid).delete().awaitTask()
    }

    // ---------- Chats and requests ----------

    /** Live list of every chat (pending or accepted) the user is part of. */
    fun listenToMyChats(
        myUid: String,
        onResult: (Result<List<ChatInfo>>) -> Unit
    ): ListenerRegistration {
        var sent: List<ChatInfo> = emptyList()
        var received: List<ChatInfo> = emptyList()
        val chats = db.collection("chats")

        val sentRegistration = chats.whereEqualTo("requesterId", myUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.failure(error))
                } else if (snapshot != null) {
                    sent = snapshot.documents.mapNotNull { it.toChatInfo() }
                    onResult(Result.success(sent + received))
                }
            }

        val receivedRegistration = chats.whereEqualTo("recipientId", myUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.failure(error))
                } else if (snapshot != null) {
                    received = snapshot.documents.mapNotNull { it.toChatInfo() }
                    onResult(Result.success(sent + received))
                }
            }

        return ListenerRegistration {
            sentRegistration.remove()
            receivedRegistration.remove()
        }
    }

    /** Live view of one chat document. Reports null if it doesn't exist. */
    fun listenToChat(
        chatId: String,
        onResult: (Result<ChatInfo?>) -> Unit
    ): ListenerRegistration =
        db.collection("chats").document(chatId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onResult(Result.failure(error))
            } else if (snapshot != null) {
                onResult(Result.success(snapshot.toChatInfo()))
            }
        }

    suspend fun createRequest(
        chatId: String,
        requesterId: String,
        recipientId: String,
        requestIv: String,
        requestCiphertext: String,
        kdfSalt: String,
        keyCheck: String,
        requesterPublicKey: String,
        recipientPublicKey: String
    ) {
        val data = hashMapOf(
            "requesterId" to requesterId,
            "recipientId" to recipientId,
            "status" to ChatInfo.STATUS_PENDING,
            "requestIv" to requestIv,
            "requestCiphertext" to requestCiphertext,
            "kdfSalt" to kdfSalt,
            "keyCheck" to keyCheck,
            "requesterPublicKey" to requesterPublicKey,
            "recipientPublicKey" to recipientPublicKey,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("chats").document(chatId).set(data).awaitTask()
    }

    suspend fun acceptRequest(chatId: String) {
        db.collection("chats").document(chatId).update(
            mapOf(
                "status" to ChatInfo.STATUS_ACCEPTED,
                "acceptedAt" to FieldValue.serverTimestamp()
            )
        ).awaitTask()
    }

    /** Declines (receiver) or cancels (sender) a pending request. */
    suspend fun deleteRequest(chatId: String) {
        db.collection("chats").document(chatId).delete().awaitTask()
    }

    /** Deletes a chat for both people: every message, then the chat itself. */
    suspend fun deleteChat(chatId: String) {
        val all = messagesOf(chatId).get().awaitTask()
        deleteMessages(chatId, all.documents.map { it.id })
        db.collection("chats").document(chatId).delete().awaitTask()
    }

    /** Resets my unread count and records when I read the chat (for read ticks). */
    suspend fun markRead(chatId: String, myUid: String) {
        db.collection("chats").document(chatId).update(
            mapOf(
                "unread_$myUid" to 0,
                "readAt_$myUid" to FieldValue.serverTimestamp()
            )
        ).awaitTask()
    }

    // ---------- Messages ----------

    /**
     * Live list of stored (still encrypted) messages, oldest first.
     * Includes metadata changes so a message switches from "sending" to "sent".
     */
    fun listenToMessages(
        chatId: String,
        onResult: (Result<List<StoredMessage>>) -> Unit
    ): ListenerRegistration =
        messagesOf(chatId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                onResult(Result.failure(error))
            } else if (snapshot != null) {
                val messages = snapshot.documents.map { doc ->
                    val ciphertext = doc.getString("ciphertext")

                    StoredMessage(
                        id = doc.id,
                        senderId = doc.getString("senderId") ?: "",
                        // ESTIMATE avoids a null timestamp while a message is pending
                        createdAt = doc.millis("createdAt") ?: Long.MAX_VALUE,
                        iv = doc.getString("iv") ?: "",
                        ciphertext = ciphertext ?: "",
                        isLegacy = ciphertext == null,
                        isPending = doc.metadata.hasPendingWrites()
                    )
                }.sortedBy { it.createdAt }

                onResult(Result.success(messages))
            }
        }

    /**
     * Saves an encrypted message and updates the chat's "last message" time and
     * the other person's unread count, all in one atomic batch.
     */
    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        recipientId: String,
        iv: String,
        ciphertext: String
    ) {
        val batch = db.batch()

        batch.set(
            messagesOf(chatId).document(),
            hashMapOf(
                "senderId" to senderId,
                "iv" to iv,
                "ciphertext" to ciphertext,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        batch.update(
            db.collection("chats").document(chatId),
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastSenderId" to senderId,
                "unread_$recipientId" to FieldValue.increment(1L)
            )
        )

        batch.commit().awaitTask()
    }

    /** Deletes the old plain-text messages (from before encryption) in a chat. */
    suspend fun deleteLegacyMessages(chatId: String) {
        val all = messagesOf(chatId).get().awaitTask()
        val legacyIds = all.documents.filter { it.contains("text") }.map { it.id }
        deleteMessages(chatId, legacyIds)
    }

    suspend fun deleteMessages(chatId: String, messageIds: List<String>) {
        val messages = messagesOf(chatId)

        // A Firestore batch holds at most 500 writes
        messageIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(messages.document(it)) }
            batch.commit().awaitTask()
        }
    }

    // ---------- Helpers ----------

    private fun messagesOf(chatId: String) =
        db.collection("chats").document(chatId).collection("messages")

    private fun blockedOf(uid: String) =
        db.collection("users").document(uid).collection("blocked")

    private fun DocumentSnapshot.millis(field: String): Long? =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time

    private fun DocumentSnapshot.toChatUser(): ChatUser? {
        val uid = getString("uid") ?: return null
        val email = getString("email") ?: return null
        return ChatUser(uid, email, getString("publicKey"), getString("displayName"))
    }

    private fun DocumentSnapshot.toChatInfo(): ChatInfo? {
        if (!exists()) return null

        val unread = mutableMapOf<String, Long>()
        val readAt = mutableMapOf<String, Long>()

        for (key in data?.keys.orEmpty()) {
            if (key.startsWith("unread_")) {
                val count = get(key) as? Number
                if (count != null) unread[key.removePrefix("unread_")] = count.toLong()
            } else if (key.startsWith("readAt_")) {
                val time = millis(key)
                if (time != null) readAt[key.removePrefix("readAt_")] = time
            }
        }

        return ChatInfo(
            chatId = id,
            requesterId = getString("requesterId") ?: return null,
            recipientId = getString("recipientId") ?: return null,
            status = getString("status") ?: return null,
            requestIv = getString("requestIv") ?: "",
            requestCiphertext = getString("requestCiphertext") ?: "",
            kdfSalt = getString("kdfSalt") ?: return null,
            keyCheck = getString("keyCheck") ?: return null,
            requesterPublicKey = getString("requesterPublicKey") ?: return null,
            recipientPublicKey = getString("recipientPublicKey") ?: return null,
            createdAt = millis("createdAt"),
            acceptedAt = millis("acceptedAt"),
            lastMessageAt = millis("lastMessageAt"),
            lastSenderId = getString("lastSenderId"),
            unread = unread,
            readAt = readAt
        )
    }
}
