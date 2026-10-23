package com.example.securechat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechat.crypto.ChatCrypto
import com.example.securechat.crypto.IdentityKeys
import com.example.securechat.crypto.LocalKeyStore
import com.example.securechat.crypto.PassphraseKeys
import com.example.securechat.crypto.fromBase64
import com.example.securechat.data.ChatRepository
import com.example.securechat.model.ChatInfo
import com.example.securechat.ui.components.AppIcons
import com.example.securechat.ui.components.Avatar
import com.example.securechat.ui.components.Banner
import com.example.securechat.ui.components.EmptyState
import com.example.securechat.ui.components.InfoCard
import com.example.securechat.ui.components.PasswordField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Three uses:
 *  - The receiver reads a chat request and enters the chat key to accept (or declines).
 *  - The sender sees their request is still waiting, and can cancel it.
 *  - Either person unlocks an accepted chat on a phone that doesn't have the key yet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeyEntryScreen(
    chatId: String,
    otherName: String,
    currentUid: String,
    identity: IdentityKeys.Identity,
    chatRepository: ChatRepository,
    localKeyStore: LocalKeyStore,
    onUnlocked: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()

    var chat by remember { mutableStateOf<ChatInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var requestText by remember { mutableStateOf<String?>(null) }
    var chatKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }

    DisposableEffect(chatId) {
        val registration = chatRepository.listenToChat(chatId) { result ->
            result
                .onSuccess {
                    chat = it
                    loaded = true
                }
                .onFailure { error ->
                    status = "Could not load this chat: " + (error.localizedMessage ?: "Unknown error")
                    loaded = true
                }
        }
        onDispose { registration.remove() }
    }

    // The request message only uses layer 2, so it can be read before the chat key is entered
    LaunchedEffect(chat?.requestCiphertext) {
        val current = chat ?: return@LaunchedEffect

        requestText = withContext(Dispatchers.Default) {
            try {
                val layer2 = IdentityKeys.layer2Key(
                    identity.privateKey,
                    IdentityKeys.decodePublicKey(current.otherPublicKey(currentUid)),
                    chatId
                )
                ChatCrypto.decryptRequest(
                    layer2, chatId, current.requesterId,
                    current.requestIv, current.requestCiphertext
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    val current = chat
    val isIncoming = current != null && current.isPending && current.recipientId == currentUid
    val isOutgoingPending = current != null && current.isPending && current.requesterId == currentUid

    fun runAction(action: suspend () -> Unit) {
        if (isWorking) return
        isWorking = true
        status = ""
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Something went wrong: " + (e.localizedMessage ?: "Unknown error")
            } finally {
                isWorking = false
            }
        }
    }

    fun submitKey(info: ChatInfo) = runAction {
        val key = chatKey
        val derived = withContext(Dispatchers.Default) {
            PassphraseKeys.derive(key, info.kdfSalt.fromBase64())
        }

        if (!PassphraseKeys.checkMatches(derived, info.keyCheck.fromBase64())) {
            status = "Wrong key. Check the key you agreed on in person and try again."
            return@runAction
        }

        if (info.myPublicKey(currentUid) != identity.publicKeyBase64) {
            status = "This chat was set up with a different security key for your account, " +
                    "so it can't be opened."
            return@runAction
        }

        localKeyStore.save(currentUid, chatId, derived.layer1Key)

        if (info.isPending && info.recipientId == currentUid) {
            chatRepository.acceptRequest(chatId)
        }

        // The right key has been entered: remove old plain-text messages.
        // If this fails, the chat screen tries again.
        try {
            chatRepository.deleteLegacyMessages(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ignored
        }

        onUnlocked()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isIncoming -> "Chat request"
                            isOutgoingPending -> "Request sent"
                            else -> "Unlock chat"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !loaded -> {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                }

                current == null -> EmptyState(
                    icon = AppIcons.Chat,
                    title = "Request not found",
                    body = "This chat request no longer exists. It may have been cancelled or declined."
                )

                else -> {
                    Spacer(Modifier.height(8.dp))
                    Avatar(otherName, size = 72.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        otherName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(20.dp))

                    when {
                        isOutgoingPending -> {
                            InfoCard(
                                icon = AppIcons.Hourglass,
                                text = "Waiting for $otherName to accept. They'll need the chat key " +
                                        "you agreed on together."
                            )
                            Spacer(Modifier.height(20.dp))
                            OutlinedButton(
                                onClick = {
                                    runAction {
                                        chatRepository.deleteRequest(chatId)
                                        onBack()
                                    }
                                },
                                enabled = !isWorking,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text("Cancel request")
                            }
                        }

                        else -> {
                            if (isIncoming) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(
                                            "Their message",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            requestText ?: "Couldn't read the request message.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontStyle = if (requestText == null) FontStyle.Italic else null
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                InfoCard(
                                    icon = AppIcons.Key,
                                    text = "To accept, enter the chat key you agreed on with " +
                                            "$otherName in person."
                                )
                            } else {
                                InfoCard(
                                    icon = AppIcons.Key,
                                    text = "This phone doesn't have this chat's key yet. Enter it once " +
                                            "to unlock the conversation."
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            PasswordField(
                                value = chatKey,
                                onValueChange = { chatKey = it },
                                label = "Chat key",
                                leadingIcon = AppIcons.Key,
                                onImeAction = { if (chatKey.isNotBlank()) submitKey(current) }
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = { submitKey(current) },
                                enabled = !isWorking && chatKey.isNotBlank(),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                if (isWorking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("Checking key...")
                                } else {
                                    Text(if (isIncoming) "Accept and open chat" else "Unlock chat")
                                }
                            }

                            if (isIncoming) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        runAction {
                                            chatRepository.deleteRequest(chatId)
                                            onBack()
                                        }
                                    },
                                    enabled = !isWorking,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Text("Decline")
                                }
                            }
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Banner(status)
            }

            if (current != null && !isOutgoingPending) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Messages are encrypted twice: with your chat key and with a security key " +
                            "unique to the two of you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}