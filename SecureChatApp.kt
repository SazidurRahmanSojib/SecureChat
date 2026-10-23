package com.example.securechat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.securechat.model.ChatInfo
import com.example.securechat.model.ChatUser
import com.example.securechat.settings.AppSettings
import com.example.securechat.ui.AuthScreen
import com.example.securechat.ui.ChatScreen
import com.example.securechat.ui.HomeScreen
import com.example.securechat.ui.KeyEntryScreen
import com.example.securechat.ui.NewChatScreen
import com.example.securechat.ui.RequestScreen
import com.example.securechat.ui.SettingsScreen
import com.example.securechat.ui.SetupScreen
import com.example.securechat.viewmodel.AppViewModel

private const val SCREEN_HOME = "home"
private const val SCREEN_NEW_CHAT = "new_chat"
private const val SCREEN_REQUEST = "request"
private const val SCREEN_KEY = "key"
private const val SCREEN_CHAT = "chat"
private const val SCREEN_SETTINGS = "settings"

@Composable
fun SecureChatApp() {
    val context = LocalContext.current
    val app: AppViewModel = viewModel()

    // rememberSaveable keeps the current screen when the phone rotates.
    var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }
    var otherUid by rememberSaveable { mutableStateOf("") }
    var otherName by rememberSaveable { mutableStateOf("") }

    // Ask once for notification permission (Android 13+) after setup finishes
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val ready = app.identity != null
    LaunchedEffect(ready) {
        if (ready &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AppSettings.notificationsAsked(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            AppSettings.markNotificationsAsked(context)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val goHome = {
        screen = SCREEN_HOME
        otherUid = ""
        otherName = ""
    }

    // Decide where tapping a person leads
    val openUser: (ChatUser, ChatInfo?) -> Unit = { selected, chat ->
        val myUid = app.currentUser?.uid ?: ""
        otherUid = selected.uid
        otherName = selected.label
        screen = when {
            chat == null -> SCREEN_REQUEST
            chat.isAccepted && app.localKeyStore.load(myUid, chat.chatId) != null -> SCREEN_CHAT
            else -> SCREEN_KEY
        }
    }

    val user = app.currentUser
    val identity = app.identity

    // Back to the start after logging out
    LaunchedEffect(user == null) {
        if (user == null) goHome()
    }

    when {
        user == null -> AuthScreen(
            authRepository = app.authRepository,
            onAuthSuccess = { app.onLoggedIn() }
        )

        identity == null -> SetupScreen(
            error = app.setupError,
            onRetry = { app.retrySetup() },
            onLogout = { app.logout() }
        )

        screen == SCREEN_SETTINGS -> SettingsScreen(
            app = app,
            onBack = goHome
        )

        screen == SCREEN_NEW_CHAT -> NewChatScreen(
            currentUid = user.uid,
            users = app.users,
            chats = app.chats,
            onSelectUser = openUser,
            onBack = goHome
        )

        screen == SCREEN_REQUEST -> RequestScreen(
            otherUid = otherUid,
            otherName = otherName,
            currentUid = user.uid,
            identity = identity,
            chatRepository = app.chatRepository,
            localKeyStore = app.localKeyStore,
            onSent = goHome,
            onBack = goHome
        )

        screen == SCREEN_KEY -> KeyEntryScreen(
            chatId = app.chatRepository.chatIdFor(user.uid, otherUid),
            otherName = otherName,
            currentUid = user.uid,
            identity = identity,
            chatRepository = app.chatRepository,
            localKeyStore = app.localKeyStore,
            onUnlocked = { screen = SCREEN_CHAT },
            onBack = goHome
        )

        screen == SCREEN_CHAT -> ChatScreen(
            chatId = app.chatRepository.chatIdFor(user.uid, otherUid),
            otherUid = otherUid,
            otherName = otherName,
            currentUid = user.uid,
            identity = identity,
            app = app,
            onNeedKey = { screen = SCREEN_KEY },
            onBack = goHome
        )

        else -> HomeScreen(
            app = app,
            onOpenChat = { selected, chat -> openUser(selected, chat) },
            onNewChat = { screen = SCREEN_NEW_CHAT },
            onOpenSettings = { screen = SCREEN_SETTINGS }
        )
    }
}
