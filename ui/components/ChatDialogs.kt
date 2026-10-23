package com.example.securechat.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** A yes/no dialog. Destructive actions get a red confirm button. */
@Composable
fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteChatDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        icon = AppIcons.Delete,
        title = "Delete chat?",
        text = "All messages with $name will be deleted for both of you. " +
                "To chat again, one of you will need to send a new request.",
        confirmLabel = "Delete",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun BlockUserDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        icon = AppIcons.Block,
        title = "Block $name?",
        text = "Your chat will be deleted, and they won't be able to message you or send " +
                "you new requests. You can unblock them later in Settings.",
        confirmLabel = "Block",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
