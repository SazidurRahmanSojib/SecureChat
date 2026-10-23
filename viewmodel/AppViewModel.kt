package com.example.securechat.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.auth.AuthRepository
import com.example.securechat.crypto.IdentityKeys
import com.example.securechat.crypto.LocalKeyStore
import com.example.securechat.data.ChatRepository
import com.example.securechat.data.HiddenMessages
import com.example.securechat.data.IdentityRepository
import com.example.securechat.model.ChatInfo
import com.example.securechat.model.ChatUser
import com.example.securechat.push.PushTokens
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Everything about the signed-in session: login state, encryption keys,
 * the live lists of users, chats and blocked people, and account actions.
 * Lives as long as the activity, so it survives screen rotation.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    val authRepository = AuthRepository()
    val chatRepository = ChatRepository()
    val localKeyStore = LocalKeyStore(application)
    private val identityRepository = IdentityRepository()
    private val hiddenMessages = HiddenMessages(application)

    var currentUser by mutableStateOf(authRepository.currentUser)
        private set
    var identity by mutableStateOf<IdentityKeys.Identity?>(null)
        private set
    var setupError by mutableStateOf("")
        private set
    var profileError by mutableStateOf("")
        private set
    var loadError by mutableStateOf("")
        private set
    var isLoggingOut by mutableStateOf(false)
        private set

    /** The signed-in user's own profile (for the display name). */
    var myProfile by mutableStateOf<ChatUser?>(null)
        private set
    private var allOtherUsers by mutableStateOf<List<ChatUser>>(emptyList())
    private var allChats by mutableStateOf<List<ChatInfo>>(emptyList())
    var blockedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Other users, without anyone this user has blocked. */
    val users: List<ChatUser>
        get() = allOtherUsers.filter { it.uid !in blockedIds }

    /** This user's chats and requests, without blocked people. */
    val chats: List<ChatInfo>
        get() {
            val uid = currentUser?.uid ?: return emptyList()
            return allChats.filter { it.otherUid(uid) !in blockedIds }
        }

    /** Every known user by ID, including blocked ones (for names in Settings). */
    val usersById: Map<String, ChatUser>
        get() = allOtherUsers.associateBy { it.uid }

    private val listeners = mutableListOf<ListenerRegistration>()
    private var setupJob: Job? = null
    private var usersError = ""
    private var chatsError = ""

    init {
        if (currentUser != null) startSession()
    }

    fun onLoggedIn() {
        currentUser = authRepository.currentUser
        startSession()
    }

    fun retrySetup() {
        startSession()
    }

    /** Loads or creates the encryption keys, saves the profile, then starts the live lists. */
    private fun startSession() {
        val user = currentUser ?: return
        if (setupJob?.isActive == true) return

        setupJob = viewModelScope.launch {
            if (identity == null) {
                setupError = ""
                try {
                    identity = identityRepository.ensureIdentity(user)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setupError = "Could not set up encryption: " + (e.localizedMessage ?: "Unknown error")
                    return@launch
                }
            }

            val keys = identity ?: return@launch
            startListeners(user.uid)

            profileError = ""
            authRepository.saveProfile(user, keys.publicKeyBase64) { result ->
                if (result.isSuccess) {
                    profileError = ""
                    PushTokens.register(user.uid)
                } else {
                    profileError = "Couldn't save your profile, so others can't find you yet: " +
                            (result.exceptionOrNull()?.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    private fun startListeners(uid: String) {
        if (listeners.isNotEmpty()) return

        listeners += chatRepository.listenToUsers { result ->
            result
                .onSuccess { all ->
                    myProfile = all.firstOrNull { it.uid == uid }
                    allOtherUsers = all.filter { it.uid != uid }
                    usersError = ""
                }
                .onFailure { usersError = "Could not load users: ${it.localizedMessage}" }
            updateLoadError()
        }

        listeners += chatRepository.listenToMyChats(uid) { result ->
            result
                .onSuccess {
                    allChats = it
                    chatsError = ""
                }
                .onFailure { chatsError = "Could not load chats: ${it.localizedMessage}" }
            updateLoadError()
        }

        listeners += chatRepository.listenToBlocked(uid) { result ->
            result.onSuccess { blockedIds = it }
        }
    }

    private fun updateLoadError() {
        loadError = listOf(usersError, chatsError).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun stopListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    override fun onCleared() {
        stopListeners()
    }

    // ---------- Account ----------

    fun logout() {
        val uid = currentUser?.uid ?: return
        if (isLoggingOut) return
        isLoggingOut = true

        viewModelScope.launch {
            // Stop notifications for this account on this phone (best effort, max 3 seconds)
            runCatching { withTimeout(3_000) { PushTokens.unregister(uid) } }

            setupJob?.cancel()
            stopListeners()
            authRepository.signOut()
            identityRepository.clearCache()

            currentUser = null
            identity = null
            myProfile = null
            allOtherUsers = emptyList()
            allChats = emptyList()
            blockedIds = emptySet()
            setupError = ""
            profileError = ""
            usersError = ""
            chatsError = ""
            loadError = ""
            isLoggingOut = false
        }
    }

    fun updateDisplayName(name: String, onResult: (String?) -> Unit) {
        val uid = currentUser?.uid ?: return
        runAction(onResult, "Could not save your name") {
            authRepository.updateDisplayName(uid, name)
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try {
                authRepository.changePassword(currentPassword, newPassword)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuthRepository.friendlyPasswordChangeMessage(e)
            }
            onResult(error)
        }
    }

    // ---------- Chats ----------

    /** Deletes a chat for both people, and forgets its key on this phone. */
    fun deleteChat(chatId: String, onResult: (String?) -> Unit = {}) {
        val uid = currentUser?.uid ?: return
        runAction(onResult, "Could not delete the chat") {
            chatRepository.deleteChat(chatId)
            forgetChat(uid, chatId)
        }
    }

    /** Blocks a user: they can't message you or send requests, and your chat is deleted. */
    fun blockUser(otherUid: String, onResult: (String?) -> Unit = {}) {
        val uid = currentUser?.uid ?: return
        runAction(onResult, "Could not block this user") {
            chatRepository.blockUser(uid, otherUid)

            val chatId = chatRepository.chatIdFor(uid, otherUid)
            if (allChats.any { it.chatId == chatId }) {
                chatRepository.deleteChat(chatId)
            }
            forgetChat(uid, chatId)
        }
    }

    fun unblockUser(otherUid: String, onResult: (String?) -> Unit = {}) {
        val uid = currentUser?.uid ?: return
        runAction(onResult, "Could not unblock this user") {
            chatRepository.unblockUser(uid, otherUid)
        }
    }

    private fun forgetChat(uid: String, chatId: String) {
        localKeyStore.remove(uid, chatId)
        hiddenMessages.clear(uid, chatId)
    }

    /** Runs a Firebase action and reports null (success) or a readable error. */
    private fun runAction(
        onResult: (String?) -> Unit,
        failurePrefix: String,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            val error = try {
                action()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "$failurePrefix: " + (e.localizedMessage ?: "Unknown error")
            }
            onResult(error)
        }
    }
}
