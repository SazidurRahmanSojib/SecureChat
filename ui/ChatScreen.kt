package com.example.securechat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.securechat.crypto.IdentityKeys
import com.example.securechat.model.ChatMessage
import com.example.securechat.push.ActiveChat
import com.example.securechat.push.Notifications
import com.example.securechat.ui.components.AppIcons
import com.example.securechat.ui.components.Avatar
import com.example.securechat.ui.components.Banner
import com.example.securechat.ui.components.BlockUserDialog
import com.example.securechat.ui.components.DayDivider
import com.example.securechat.ui.components.DeleteChatDialog
import com.example.securechat.ui.components.EmptyState
import com.example.securechat.ui.components.MessageBubble
import com.example.securechat.ui.components.MessageStatus
import com.example.securechat.ui.components.copyToClipboard
import com.example.securechat.ui.components.formatDayLabel
import com.example.securechat.ui.components.formatMessageTime
import com.example.securechat.ui.components.isSameDay
import com.example.securechat.ui.components.rememberIsResumed
import com.example.securechat.viewmodel.AppViewModel
import com.example.securechat.viewmodel.ChatViewModel

private const val MAX_MESSAGE_LENGTH = 4000

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUid: String,
    otherName: String,
    currentUid: String,
    identity: IdentityKeys.Identity,
    app: AppViewModel,
    onNeedKey: () -> Unit,
    onBack: () -> Unit
) {
    // The phone's Back button/gesture returns to the chat list instead of closing the app.
    BackHandler(onBack = onBack)

    val vm: ChatViewModel = viewModel(key = "chat_$chatId")
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme

    // Saveable so a half-typed message survives screen rotation.
    var draft by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var messageMenuFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf("") }

    // Listen while this screen is shown
    DisposableEffect(chatId) {
        vm.bind(chatId, currentUid, identity)
        vm.start()
        onDispose { vm.stop() }
    }

    // While the chat is on screen: skip its notifications and clear any already shown
    val resumed by rememberIsResumed()
    DisposableEffect(chatId, resumed) {
        if (resumed) {
            ActiveChat.chatId = chatId
            Notifications.cancel(context, chatId)
        }
        onDispose {
            if (ActiveChat.chatId == chatId) ActiveChat.chatId = null
        }
    }

    // Mark as read while the chat is visible and the app is in the foreground
    LaunchedEffect(resumed, vm.chat, vm.messages) {
        if (resumed) vm.markReadIfNeeded()
    }

    LaunchedEffect(vm.needKey) {
        if (vm.needKey) onNeedKey()
    }

    val messages = vm.messages

    // Scroll to the newest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Each message, plus whether a date divider goes above it
    val rows = remember(messages) {
        messages.mapIndexed { index, message ->
            val newDay = index == 0 || !isSameDay(messages[index - 1].createdAt, message.createdAt)
            message to newDay
        }
    }

    val otherReadAt = vm.chat?.readAtFor(otherUid) ?: 0L
    fun statusOf(msg: ChatMessage): MessageStatus = when {
        msg.isPending -> MessageStatus.SENDING
        msg.createdAt <= otherReadAt -> MessageStatus.READ
        else -> MessageStatus.SENT
    }

    val chatDeleted = vm.chatLoaded && vm.chat == null
    val canSend = vm.keys != null && !chatDeleted

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || !canSend) return
        draft = ""
        vm.send(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(otherName, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                otherName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.Lock,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (vm.keys == null) "Unlocking..." else "End-to-end encrypted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(AppIcons.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete chat") },
                                leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block $otherName") },
                                leadingIcon = { Icon(AppIcons.Block, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmBlock = true
                                }
                            )
                        }
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
        ) {
            val error = listOf(vm.status, actionError).filter { it.isNotBlank() }.joinToString("\n")
            if (error.isNotBlank() && !chatDeleted) {
                Banner(error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    chatDeleted -> EmptyState(
                        icon = AppIcons.Delete,
                        title = "This chat was deleted",
                        body = "Go back and send a new request to chat with $otherName again.",
                        modifier = Modifier.align(Alignment.Center)
                    )

                    vm.keys == null && vm.status.isBlank() -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )

                    vm.keys != null && vm.messagesLoaded && messages.isEmpty() -> EmptyState(
                        icon = AppIcons.Lock,
                        title = "Say hello",
                        body = "Messages here are encrypted with your chat key. " +
                                "Only you and $otherName can read them.",
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(rows, key = { it.first.id }) { (msg, newDay) ->
                            if (newDay) {
                                DayDivider(formatDayLabel(msg.createdAt))
                            }

                            val isMine = msg.senderId == currentUid

                            MessageBubble(
                                text = msg.text ?: "Couldn't decrypt this message",
                                time = formatMessageTime(msg.createdAt),
                                isMine = isMine,
                                failed = msg.text == null,
                                status = if (isMine) statusOf(msg) else null,
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    messageMenuFor = msg.id
                                },
                                menu = {
                                    DropdownMenu(
                                        expanded = messageMenuFor == msg.id,
                                        onDismissRequest = { messageMenuFor = null }
                                    ) {
                                        val text = msg.text
                                        if (text != null) {
                                            DropdownMenuItem(
                                                text = { Text("Copy") },
                                                leadingIcon = { Icon(AppIcons.Copy, contentDescription = null) },
                                                onClick = {
                                                    messageMenuFor = null
                                                    context.copyToClipboard(text)
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Delete for me") },
                                            leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                                            onClick = {
                                                messageMenuFor = null
                                                vm.deleteForMe(msg.id)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Message input
            Surface(color = colors.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it.take(MAX_MESSAGE_LENGTH) },
                        placeholder = { Text("Message") },
                        enabled = canSend,
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = colors.surfaceContainerHigh,
                            unfocusedContainerColor = colors.surfaceContainerHigh,
                            disabledContainerColor = colors.surfaceContainerHigh
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = { send() },
                        enabled = canSend && draft.isNotBlank(),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(AppIcons.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteChatDialog(
            name = otherName,
            onConfirm = {
                confirmDelete = false
                app.deleteChat(chatId) { error ->
                    if (error == null) onBack() else actionError = error
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }

    if (confirmBlock) {
        BlockUserDialog(
            name = otherName,
            onConfirm = {
                confirmBlock = false
                app.blockUser(otherUid) { error ->
                    if (error == null) onBack() else actionError = error
                }
            },
            onDismiss = { confirmBlock = false }
        )
    }
}
