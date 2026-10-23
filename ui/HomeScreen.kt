package com.example.securechat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechat.model.ChatInfo
import com.example.securechat.model.ChatUser
import com.example.securechat.ui.components.AppIcons
import com.example.securechat.ui.components.Avatar
import com.example.securechat.ui.components.Banner
import com.example.securechat.ui.components.BlockUserDialog
import com.example.securechat.ui.components.DeleteChatDialog
import com.example.securechat.ui.components.EmptyState
import com.example.securechat.ui.components.Pill
import com.example.securechat.ui.components.SectionHeader
import com.example.securechat.ui.components.formatChatListTime
import com.example.securechat.viewmodel.AppViewModel

/**
 * The main screen: incoming requests, then chats sorted by latest message,
 * with unread counts. Long-press a chat to delete it or block the person.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: AppViewModel,
    onOpenChat: (user: ChatUser, chat: ChatInfo) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val currentUid = app.currentUser?.uid ?: return
    val usersById = app.usersById
    val chats = app.chats

    var actionError by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Pair<ChatUser, ChatInfo>?>(null) }
    var blockTarget by remember { mutableStateOf<ChatUser?>(null) }

    fun userFor(chat: ChatInfo): ChatUser {
        val uid = chat.otherUid(currentUid)
        return usersById[uid] ?: ChatUser(uid, "Unknown user")
    }

    val incoming = chats
        .filter { it.isPending && it.recipientId == currentUid }
        .sortedByDescending { it.lastActivity }

    // Newest activity first
    val others = chats
        .filterNot { it.isPending && it.recipientId == currentUid }
        .sortedByDescending { it.lastActivity }

    val totalUnread = chats.sumOf { it.unreadFor(currentUid) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chats", style = MaterialTheme.typography.titleLarge)
                        Text(
                            when {
                                totalUnread == 1L -> "1 unread message"
                                totalUnread > 1L -> "$totalUnread unread messages"
                                else -> app.myProfile?.label ?: app.currentUser?.email ?: ""
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (totalUnread > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(AppIcons.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text("New chat") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                // Leave room so the button never covers the last chat
                bottom = padding.calculateBottomPadding() + 96.dp
            )
        ) {
            if (app.profileError.isNotBlank()) {
                item {
                    Banner(
                        app.profileError,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        actionLabel = "Retry",
                        onAction = { app.retrySetup() }
                    )
                }
            }

            val errors = listOf(app.loadError, actionError).filter { it.isNotBlank() }
            if (errors.isNotEmpty()) {
                item {
                    Banner(
                        errors.joinToString("\n"),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        actionLabel = if (actionError.isNotBlank()) "OK" else null,
                        onAction = { actionError = "" }
                    )
                }
            }

            if (incoming.isNotEmpty()) {
                item { SectionHeader("Requests") }

                items(incoming, key = { "in_" + it.chatId }) { chat ->
                    val user = userFor(chat)
                    ChatRow(
                        title = user.label,
                        subtitle = "Wants to chat with you",
                        highlight = true,
                        time = formatChatListTime(chat.lastActivity),
                        trailing = { Pill("New", highlighted = true) },
                        onClick = { onOpenChat(user, chat) },
                        onDelete = { deleteTarget = user to chat },
                        onBlock = { blockTarget = user },
                        deleteLabel = "Decline request"
                    )
                }
            }

            if (others.isNotEmpty()) {
                item { SectionHeader("Chats") }

                items(others, key = { it.chatId }) { chat ->
                    val user = userFor(chat)
                    val unread = chat.unreadFor(currentUid)
                    val otherReadAt = chat.readAtFor(user.uid) ?: 0L
                    val lastAt = chat.lastMessageAt

                    val subtitle = when {
                        chat.isPending -> "Request sent · waiting for them to accept"
                        unread == 1L -> "1 new message"
                        unread > 1L -> "$unread new messages"
                        lastAt == null -> "End-to-end encrypted · say hello"
                        chat.lastSenderId == currentUid && otherReadAt >= lastAt -> "You sent a message · Seen"
                        chat.lastSenderId == currentUid -> "You sent a message"
                        else -> "Encrypted message"
                    }

                    ChatRow(
                        title = user.label,
                        subtitle = subtitle,
                        highlight = unread > 0,
                        time = if (chat.isPending) "" else formatChatListTime(chat.lastActivity),
                        trailing = {
                            when {
                                chat.isPending -> Pill("Pending")
                                unread > 0 -> UnreadBadge(unread)
                            }
                        },
                        onClick = { onOpenChat(user, chat) },
                        onDelete = { deleteTarget = user to chat },
                        onBlock = { blockTarget = user },
                        deleteLabel = if (chat.isPending) "Cancel request" else "Delete chat"
                    )
                }
            }

            if (chats.isEmpty()) {
                item {
                    EmptyState(
                        icon = AppIcons.Chat,
                        title = "No chats yet",
                        body = "Tap New chat and search for someone by their name or email to send a chat request."
                    )
                }
            }
        }
    }

    deleteTarget?.let { (user, chat) ->
        DeleteChatDialog(
            name = user.label,
            onConfirm = {
                deleteTarget = null
                app.deleteChat(chat.chatId) { error -> actionError = error ?: "" }
            },
            onDismiss = { deleteTarget = null }
        )
    }

    blockTarget?.let { user ->
        BlockUserDialog(
            name = user.label,
            onConfirm = {
                blockTarget = null
                app.blockUser(user.uid) { error -> actionError = error ?: "" }
            },
            onDismiss = { blockTarget = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    title: String,
    subtitle: String,
    highlight: Boolean,
    time: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    deleteLabel: String
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box {
        ListItem(
            headlineContent = {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
                )
            },
            supportingContent = {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (highlight) FontWeight.SemiBold else null,
                    color = if (highlight) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
            },
            leadingContent = { Avatar(title) },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    if (time.isNotEmpty()) {
                        Text(
                            time,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (highlight) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    trailing()
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                )
                .padding(horizontal = 4.dp)
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(deleteLabel) },
                leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
            DropdownMenuItem(
                text = { Text("Block") },
                leadingIcon = { Icon(AppIcons.Block, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onBlock()
                }
            )
        }
    }
}

@Composable
private fun UnreadBadge(count: Long) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
