package com.example.securechat.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechat.auth.AuthRepository
import com.example.securechat.push.Notifications
import com.example.securechat.settings.AppSettings
import com.example.securechat.settings.ThemeMode
import com.example.securechat.ui.components.AppIcons
import com.example.securechat.ui.components.Avatar
import com.example.securechat.ui.components.Banner
import com.example.securechat.ui.components.ConfirmDialog
import com.example.securechat.ui.components.PasswordField
import com.example.securechat.ui.components.SectionHeader
import com.example.securechat.ui.components.rememberIsResumed
import com.example.securechat.viewmodel.AppViewModel

private const val MIN_PASSWORD_LENGTH = 8

/** Profile, password, theme, notifications, blocked people and logout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: AppViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    var showNameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var messageIsError by remember { mutableStateOf(false) }

    fun report(error: String?, success: String) {
        messageIsError = error != null
        message = error ?: success
    }

    // Notification permission (Android 13+); rechecked when coming back from system settings
    val resumed by rememberIsResumed()
    var permissionVersion by remember { mutableIntStateOf(0) }
    val notificationsAllowed = remember(resumed, permissionVersion) { Notifications.canNotify(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionVersion++ }

    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val profile = app.myProfile
    val email = app.currentUser?.email ?: ""
    val name = profile?.label ?: email

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // ----- Profile card -----
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name, size = 60.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (profile?.hasDisplayName == true) {
                            Text(
                                email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { showNameDialog = true }) {
                        Icon(AppIcons.Edit, contentDescription = "Edit name")
                    }
                }
            }

            if (message.isNotBlank()) {
                Banner(
                    message,
                    isError = messageIsError,
                    actionLabel = "OK",
                    onAction = { message = "" },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ----- Account -----
            SectionHeader("Account")
            SettingsRow(
                icon = AppIcons.Person,
                title = "Display name",
                subtitle = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Not set · others see your email",
                onClick = { showNameDialog = true }
            )
            SettingsRow(
                icon = AppIcons.Key,
                title = "Change password",
                subtitle = "Your chats and keys are not affected",
                onClick = { showPasswordDialog = true }
            )

            // ----- Appearance -----
            SectionHeader("Appearance")
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = AppSettings.themeMode == mode,
                            onClick = { AppSettings.setTheme(context, mode) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = AppSettings.themeMode == mode, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // ----- Notifications -----
            SectionHeader("Notifications")
            SettingsRow(
                icon = AppIcons.Notifications,
                title = if (notificationsAllowed) "Notifications are on" else "Notifications are off",
                subtitle = "Shows \"New encrypted message\" or \"New chat request\". " +
                        "Message text is never shown.",
                onClick = {
                    if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !AppSettings.notificationsAsked(context)
                    ) {
                        AppSettings.markNotificationsAsked(context)
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings()
                    }
                }
            )

            // ----- Blocked people -----
            SectionHeader("Blocked people")
            if (app.blockedIds.isEmpty()) {
                Text(
                    "You haven't blocked anyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                app.blockedIds.sorted().forEach { uid ->
                    val blockedName = app.usersById[uid]?.label ?: "Unknown user"
                    ListItem(
                        headlineContent = {
                            Text(blockedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { Avatar(blockedName, size = 40.dp) },
                        trailingContent = {
                            TextButton(onClick = {
                                app.unblockUser(uid) { error -> report(error, "$blockedName was unblocked.") }
                            }) {
                                Text("Unblock")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // ----- Log out -----
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { confirmLogout = true },
                enabled = !app.isLoggingOut,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp)
            ) {
                if (app.isLoggingOut) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Logging out...")
                } else {
                    Icon(AppIcons.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log out")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "SecureChat · two-layer end-to-end encryption",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }

    if (showNameDialog) {
        NameDialog(
            initial = profile?.displayName ?: "",
            onSave = { newName, onDone ->
                app.updateDisplayName(newName) { error ->
                    onDone(error)
                    if (error == null) {
                        showNameDialog = false
                        report(null, if (newName.isBlank()) "Name removed." else "Name saved.")
                    }
                }
            },
            onDismiss = { showNameDialog = false }
        )
    }

    if (showPasswordDialog) {
        PasswordDialog(
            onSave = { current, new, onDone ->
                app.changePassword(current, new) { error ->
                    onDone(error)
                    if (error == null) {
                        showPasswordDialog = false
                        report(null, "Password changed.")
                    }
                }
            },
            onDismiss = { showPasswordDialog = false }
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            icon = AppIcons.Logout,
            title = "Log out?",
            text = "Your chat keys stay saved on this phone, so you won't need to enter them " +
                    "again when you log back in.",
            confirmLabel = "Log out",
            destructive = false,
            onConfirm = {
                confirmLogout = false
                app.logout()
            },
            onDismiss = { confirmLogout = false }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(
                AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
    )
}

/** Edit the display name. Saving an empty name removes it. */
@Composable
private fun NameDialog(
    initial: String,
    onSave: (name: String, onDone: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(AppIcons.Person, contentDescription = null) },
        title = { Text("Display name") },
        text = {
            Column {
                Text("This is the name other people see instead of your email. Leave it empty to show your email.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(AuthRepository.MAX_NAME_LENGTH) },
                    label = { Text("Name") },
                    singleLine = true,
                    supportingText = { Text("${name.length} / ${AuthRepository.MAX_NAME_LENGTH}") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    error = ""
                    onSave(name.trim()) { result ->
                        saving = false
                        error = result ?: ""
                    }
                }
            ) {
                Text(if (saving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

/** Change password: current password, then the new one twice. */
@Composable
private fun PasswordDialog(
    onSave: (current: String, new: String, onDone: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun save() {
        error = when {
            current.isEmpty() -> "Enter your current password."
            newPassword.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
            newPassword != confirm -> "The new passwords don't match."
            newPassword == current -> "The new password must be different."
            else -> ""
        }
        if (error.isNotEmpty()) return

        saving = true
        onSave(current, newPassword) { result ->
            saving = false
            error = result ?: ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(AppIcons.Key, contentDescription = null) },
        title = { Text("Change password") },
        text = {
            Column {
                PasswordField(
                    value = current,
                    onValueChange = { current = it },
                    label = "Current password",
                    imeAction = ImeAction.Next,
                    enabled = !saving
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New password",
                    imeAction = ImeAction.Next,
                    supportingText = "At least $MIN_PASSWORD_LENGTH characters",
                    enabled = !saving
                )
                Spacer(Modifier.height(4.dp))
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Repeat new password",
                    onImeAction = { save() },
                    isError = confirm.isNotEmpty() && confirm != newPassword,
                    enabled = !saving
                )
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { save() }, enabled = !saving) {
                Text(if (saving) "Saving..." else "Change")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
