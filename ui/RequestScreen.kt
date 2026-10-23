package com.example.securechat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechat.crypto.ChatCrypto
import com.example.securechat.crypto.IdentityKeys
import com.example.securechat.crypto.LocalKeyStore
import com.example.securechat.crypto.PassphraseKeys
import com.example.securechat.crypto.toBase64
import com.example.securechat.data.ChatRepository
import com.example.securechat.ui.components.AppIcons
import com.example.securechat.ui.components.Avatar
import com.example.securechat.ui.components.Banner
import com.example.securechat.ui.components.InfoCard
import com.example.securechat.ui.components.PasswordField
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_REQUEST_LENGTH = 300

/** The sender writes a short request message and sets the chat key agreed on in person. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RequestScreen(
    otherUid: String,
    otherName: String,
    currentUid: String,
    identity: IdentityKeys.Identity,
    chatRepository: ChatRepository,
    localKeyStore: LocalKeyStore,
    onSent: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()

    var requestMessage by rememberSaveable { mutableStateOf("") }
    // Keys use plain remember so they are never written to saved state
    var chatKey by remember { mutableStateOf("") }
    var confirmKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }

    fun send() {
        if (isWorking) return
        val text = requestMessage.trim()
        val key = chatKey

        status = when {
            text.isEmpty() -> "Write a short message so they know who you are."
            !PassphraseKeys.isLongEnough(key) ->
                "The chat key must be at least ${PassphraseKeys.MIN_LENGTH} characters."
            PassphraseKeys.normalize(key) != PassphraseKeys.normalize(confirmKey) ->
                "The two keys don't match."
            else -> ""
        }
        if (status.isNotEmpty()) return

        isWorking = true

        scope.launch {
            try {
                val recipientKey = chatRepository.getUser(otherUid)?.publicKey
                if (recipientKey == null) {
                    status = "$otherName hasn't set up encryption yet. " +
                            "Ask them to log in to the updated app."
                    return@launch
                }

                val chatId = chatRepository.chatIdFor(currentUid, otherUid)

                // The slow key stretching runs off the main thread
                val prepared = withContext(Dispatchers.Default) {
                    val salt = PassphraseKeys.newSalt()
                    val derived = PassphraseKeys.derive(key, salt)
                    val layer2 = IdentityKeys.layer2Key(
                        identity.privateKey,
                        IdentityKeys.decodePublicKey(recipientKey),
                        chatId
                    )
                    val request = ChatCrypto.encryptRequest(layer2, chatId, currentUid, text)
                    Triple(salt, derived, request)
                }
                val (salt, derived, request) = prepared

                chatRepository.createRequest(
                    chatId = chatId,
                    requesterId = currentUid,
                    recipientId = otherUid,
                    requestIv = request.iv,
                    requestCiphertext = request.ciphertext,
                    kdfSalt = salt.toBase64(),
                    keyCheck = derived.keyCheck.toBase64(),
                    requesterPublicKey = identity.publicKeyBase64,
                    recipientPublicKey = recipientKey
                )

                localKeyStore.save(currentUid, chatId, derived.layer1Key)
                onSent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val denied = e is FirebaseFirestoreException &&
                        e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                status = if (denied) {
                    "$otherName isn't accepting chat requests from you right now."
                } else {
                    "Could not send the request: " + (e.localizedMessage ?: "Unknown error")
                }
            } finally {
                isWorking = false
            }
        }
    }

    val keyLength = PassphraseKeys.normalize(chatKey).length
    val keyMismatch = confirmKey.isNotEmpty() &&
            PassphraseKeys.normalize(confirmKey) != PassphraseKeys.normalize(chatKey)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New chat request") },
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Who the request is going to
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(otherName, size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            otherName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Not connected yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Introduce yourself", style = MaterialTheme.typography.titleMedium)
            Text(
                "They can read this before entering the chat key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = requestMessage,
                onValueChange = { requestMessage = it.take(MAX_REQUEST_LENGTH) },
                placeholder = { Text("Hi! It's Sojib from the meetup.") },
                supportingText = { Text("${requestMessage.length} / $MAX_REQUEST_LENGTH") },
                minLines = 3,
                maxLines = 5,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text("Set a chat key", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            InfoCard(
                icon = AppIcons.Key,
                text = "Agree on this key with $otherName in person. It never leaves your phones, " +
                        "and they'll need it to accept. A few random words work well."
            )

            Spacer(Modifier.height(12.dp))

            PasswordField(
                value = chatKey,
                onValueChange = { chatKey = it },
                label = "Chat key",
                leadingIcon = AppIcons.Key,
                imeAction = ImeAction.Next,
                supportingText = if (keyLength >= PassphraseKeys.MIN_LENGTH) {
                    "Long enough"
                } else {
                    "$keyLength / ${PassphraseKeys.MIN_LENGTH} characters minimum"
                }
            )

            Spacer(Modifier.height(4.dp))

            PasswordField(
                value = confirmKey,
                onValueChange = { confirmKey = it },
                label = "Repeat chat key",
                leadingIcon = AppIcons.Key,
                imeAction = ImeAction.Done,
                onImeAction = { send() },
                isError = keyMismatch,
                supportingText = if (keyMismatch) "Keys don't match" else null
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { send() },
                enabled = !isWorking,
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
                    Text("Securing request...")
                } else {
                    Icon(AppIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send request")
                }
            }

            if (status.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Banner(status)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
