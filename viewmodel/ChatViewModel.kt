package com.example.securechat.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.crypto.ChatCrypto
import com.example.securechat.crypto.ChatKeys
import com.example.securechat.crypto.IdentityKeys
import com.example.securechat.crypto.LocalKeyStore
import com.example.securechat.data.ChatRepository
import com.example.securechat.data.HiddenMessages
import com.example.securechat.model.ChatInfo
import com.example.securechat.model.ChatMessage
import com.example.securechat.model.StoredMessage
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * One open chat: listens to the chat and its messages, builds both keys,
 * decrypts off the main thread, sends, marks as read, and hides messages
 * ("delete for me"). Kept per chat, so rotating the screen doesn't decrypt again.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository()
    private val localKeyStore = LocalKeyStore(application)
    private val hiddenMessages = HiddenMessages(application)

    private var chatId = ""
    private var myUid = ""
    private var identity: IdentityKeys.Identity? = null

    var chat by mutableStateOf<ChatInfo?>(null)
        private set
    /** True once the chat document has loaded (it may be null if the chat was deleted). */
    var chatLoaded by mutableStateOf(false)
        private set
    var keys by mutableStateOf<ChatKeys?>(null)
        private set
    /** This phone doesn't have the chat key yet: the screen should ask for it. */
    var needKey by mutableStateOf(false)
        private set
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var messagesLoaded by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set

    private var stored: List<StoredMessage> = emptyList()
    private var hiddenIds: Set<String> = emptySet()
    private var keysSalt: String? = null
    private var buildingKeys = false
    private var deletingLegacy = false
    private var markingRead = false

    // Each message is decrypted once, then remembered by its ID (null = couldn't decrypt)
    private class Decrypted(val text: String?)
    private val decrypted = ConcurrentHashMap<String, Decrypted>()

    private var chatRegistration: ListenerRegistration? = null
    private var messagesRegistration: ListenerRegistration? = null
    private var decryptJob: Job? = null

    fun bind(chatId: String, myUid: String, identity: IdentityKeys.Identity) {
        if (this.chatId == chatId && this.myUid == myUid) return
        this.chatId = chatId
        this.myUid = myUid
        this.identity = identity
        hiddenIds = hiddenMessages.load(myUid, chatId)
    }

    /** Start listening (when the chat screen appears). */
    fun start() {
        if (chatRegistration != null || chatId.isEmpty()) return
        needKey = false

        chatRegistration = repository.listenToChat(chatId) { result ->
            result
                .onSuccess { onChat(it) }
                .onFailure { status = "Could not load chat: " + (it.localizedMessage ?: "Unknown error") }
        }

        messagesRegistration = repository.listenToMessages(chatId) { result ->
            result
                .onSuccess {
                    stored = it
                    messagesLoaded = true
                    refresh()
                }
                .onFailure { status = "Could not load messages: " + (it.localizedMessage ?: "Unknown error") }
        }
    }

    /** Stop listening (when the chat screen goes away). */
    fun stop() {
        chatRegistration?.remove()
        messagesRegistration?.remove()
        chatRegistration = null
        messagesRegistration = null
    }

    override fun onCleared() {
        stop()
    }

    private fun onChat(info: ChatInfo?) {
        chat = info
        chatLoaded = true

        // Deleted, or deleted and requested again with a new key: drop the old keys
        if (info == null || (keysSalt != null && info.kdfSalt != keysSalt)) {
            resetKeys()
        }
        if (info != null && keys == null) buildKeys(info)
    }

    private fun resetKeys() {
        keys = null
        keysSalt = null
        decrypted.clear()
        messages = emptyList()
    }

    private fun buildKeys(info: ChatInfo) {
        if (buildingKeys) return

        if (!info.isAccepted) {
            status = "This chat isn't active yet."
            return
        }

        val layer1 = localKeyStore.load(myUid, chatId)
        if (layer1 == null) {
            needKey = true
            return
        }

        val myIdentity = identity ?: return
        buildingKeys = true

        viewModelScope.launch {
            try {
                keys = withContext(Dispatchers.Default) {
                    val layer2 = IdentityKeys.layer2Key(
                        myIdentity.privateKey,
                        IdentityKeys.decodePublicKey(info.otherPublicKey(myUid)),
                        chatId
                    )
                    ChatKeys(chatId, layer1, layer2)
                }
                keysSalt = info.kdfSalt
                status = ""
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Could not set up this chat's encryption keys."
            } finally {
                buildingKeys = false
            }
        }
    }

    /** Decrypts whatever changed and rebuilds the visible message list. */
    private fun refresh() {
        val chatKeys = keys ?: return
        val snapshot = stored
        val hidden = hiddenIds

        decryptJob?.cancel()
        decryptJob = viewModelScope.launch {
            messages = withContext(Dispatchers.Default) {
                snapshot
                    .filter { !it.isLegacy && it.id !in hidden }
                    .map { m ->
                        val text = decrypted.getOrPut(m.id) {
                            Decrypted(ChatCrypto.decryptMessage(chatKeys, m.senderId, m.iv, m.ciphertext))
                        }.text
                        ChatMessage(m.id, m.senderId, text, m.createdAt, m.isPending)
                    }
            }
        }

        deleteLegacyMessages()
    }

    /** Old plain-text messages are deleted once the right key is in place. */
    private fun deleteLegacyMessages() {
        if (keys == null || deletingLegacy) return
        val legacyIds = stored.filter { it.isLegacy }.map { it.id }
        if (legacyIds.isEmpty()) return

        deletingLegacy = true
        viewModelScope.launch {
            try {
                repository.deleteMessages(chatId, legacyIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Tried again next time
            } finally {
                deletingLegacy = false
            }
        }
    }

    fun send(text: String) {
        val chatKeys = keys ?: return
        val current = chat ?: return
        val recipientId = current.otherUid(myUid)

        viewModelScope.launch {
            try {
                val sealed = withContext(Dispatchers.Default) {
                    ChatCrypto.encryptMessage(chatKeys, myUid, text)
                }
                repository.sendMessage(chatId, myUid, recipientId, sealed.iv, sealed.ciphertext)
                status = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Message failed: " + (e.localizedMessage ?: "Unknown error")
            }
        }
    }

    /** "Delete for me": hide the message on this phone only. */
    fun deleteForMe(messageId: String) {
        hiddenIds = hiddenMessages.hide(myUid, chatId, messageId)
        refresh()
    }

    /** Clears my unread count and updates the read time, if anything new arrived. */
    fun markReadIfNeeded() {
        val current = chat ?: return
        if (!current.isAccepted || markingRead) return

        val readAt = current.readAtFor(myUid) ?: 0L
        val newestFromOther = messages
            .filter { it.senderId != myUid && !it.isPending }
            .maxOfOrNull { it.createdAt } ?: 0L

        if (current.unreadFor(myUid) == 0L && newestFromOther <= readAt) return

        markingRead = true
        viewModelScope.launch {
            try {
                repository.markRead(chatId, myUid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not important; tried again when the next message arrives
            } finally {
                markingRead = false
            }
        }
    }
}
