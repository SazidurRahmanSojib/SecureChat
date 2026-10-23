package com.example.securechat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.securechat.model.ChatInfo
import com.example.securechat.model.ChatUser
import com.example.securechat.ui.components.AppIcons
import com.example.securechat.ui.components.Avatar
import com.example.securechat.ui.components.Banner
import com.example.securechat.ui.components.EmptyState

private const val MIN_SEARCH_LENGTH = 3
private const val MAX_RESULTS = 30

/** Find someone by part of their name or email, then send them a chat request. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewChatScreen(
    currentUid: String,
    users: List<ChatUser>,
    chats: List<ChatInfo>,
    onSelectUser: (user: ChatUser, chat: ChatInfo?) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var query by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Open the keyboard straight away
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val chatsByUser = remember(chats) { chats.associateBy { it.otherUid(currentUid) } }
    val search = query.trim().lowercase()

    fun matches(user: ChatUser): Boolean =
        user.email.lowercase().contains(search) ||
                (user.displayName?.lowercase()?.contains(search) == true)

    fun startsWith(user: ChatUser): Boolean =
        user.email.lowercase().startsWith(search) ||
                (user.displayName?.lowercase()?.startsWith(search) == true)

    // Matching users; those whose name or email starts with the search come first
    val results = if (search.length < MIN_SEARCH_LENGTH) {
        emptyList()
    } else {
        users
            .filter { matches(it) }
            .sortedWith(compareBy<ChatUser>({ if (startsWith(it)) 0 else 1 }, { it.label.lowercase() }))
            .take(MAX_RESULTS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New chat") },
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
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    notice = ""
                },
                placeholder = { Text("Search by name or email") },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(AppIcons.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
            )

            if (notice.isNotBlank()) {
                Banner(
                    notice,
                    isError = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            when {
                search.length < MIN_SEARCH_LENGTH -> EmptyState(
                    icon = AppIcons.Search,
                    title = "Find someone to chat with",
                    body = "Type at least $MIN_SEARCH_LENGTH characters of their name or email address."
                )

                results.isEmpty() -> EmptyState(
                    icon = AppIcons.Search,
                    title = "No one found",
                    body = "No registered user matches \"${query.trim()}\". " +
                            "Check the spelling, or ask them to create an account first."
                )

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(results, key = { it.uid }) { user ->
                        val chat = chatsByUser[user.uid]
                        val notReady = chat == null && user.publicKey == null

                        val status = when {
                            notReady -> "Hasn't set up encryption yet"
                            chat == null -> "Tap to send a chat request"
                            chat.isPending && chat.recipientId == currentUid -> "Wants to chat with you"
                            chat.isPending -> "Request sent · waiting"
                            else -> "Already chatting · tap to open"
                        }

                        val primary = MaterialTheme.colorScheme.primary

                        ListItem(
                            headlineContent = {
                                Text(
                                    highlight(user.label, search, primary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Column {
                                    if (user.hasDisplayName) {
                                        Text(
                                            highlight(user.email, search, primary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        status,
                                        color = if (notReady) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            Color.Unspecified
                                        }
                                    )
                                }
                            },
                            leadingContent = { Avatar(user.label) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                if (notReady) {
                                    notice = "${user.label} needs to log in to the updated app " +
                                            "before you can send them a request."
                                } else {
                                    onSelectUser(user, chat)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Makes the matching part of the text bold and colored. */
private fun highlight(text: String, search: String, color: Color): AnnotatedString {
    val lower = text.lowercase()
    val start = lower.indexOf(search)

    // Lowercasing can change the length of some characters; fall back to plain text then
    if (search.isEmpty() || start < 0 || lower.length != text.length) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
            append(text.substring(start, start + search.length))
        }
        append(text.substring(start + search.length))
    }
}
