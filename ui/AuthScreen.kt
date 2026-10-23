package com.example.securechat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.securechat.auth.AuthRepository
import com.example.securechat.ui.components.AppIcons
import com.example.securechat.ui.components.Banner
import com.example.securechat.ui.components.IconBadge
import com.example.securechat.ui.components.PasswordField

private const val MIN_PASSWORD_LENGTH = 8

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit
) {
    // Email and the login/register mode survive rotation.
    // Passwords use plain remember so they are never written to saved state.
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isRegistering by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showReset by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme

    fun submit() {
        if (isLoading) return
        val cleanEmail = email.trim()

        message = when {
            cleanEmail.isBlank() || password.isBlank() ->
                "Enter your email and password."

            isRegistering && password.length < MIN_PASSWORD_LENGTH ->
                "Use at least $MIN_PASSWORD_LENGTH characters for your password."

            isRegistering && password != confirmPassword ->
                "The two passwords don't match."

            else -> ""
        }
        if (message.isNotEmpty()) return

        focusManager.clearFocus()
        isLoading = true

        val handleResult: (Result<Unit>) -> Unit = { result ->
            isLoading = false
            if (result.isSuccess) {
                onAuthSuccess()
            } else {
                message = AuthRepository.friendlyMessage(result.exceptionOrNull())
            }
        }

        if (isRegistering) {
            authRepository.register(cleanEmail, password, handleResult)
        } else {
            authRepository.signIn(cleanEmail, password, handleResult)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.primaryContainer, colors.background, colors.background)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            IconBadge(AppIcons.Lock)

            Spacer(Modifier.height(20.dp))

            Text(
                "SecureChat",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onBackground
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "Private conversations, locked with keys only you and your friends know.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.surfaceContainerLowest,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        if (isRegistering) "Create your account" else "Welcome back",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (isRegistering) "It only takes a minute." else "Log in to continue your chats.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(AppIcons.Mail, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        imeAction = if (isRegistering) ImeAction.Next else ImeAction.Done,
                        onImeAction = {
                            if (isRegistering) focusManager.moveFocus(FocusDirection.Down) else submit()
                        },
                        supportingText = if (isRegistering) "At least $MIN_PASSWORD_LENGTH characters" else null
                    )

                    if (isRegistering) {
                        Spacer(Modifier.height(8.dp))

                        val mismatch = confirmPassword.isNotEmpty() && confirmPassword != password
                        PasswordField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = "Confirm password",
                            imeAction = ImeAction.Done,
                            onImeAction = { submit() },
                            isError = mismatch,
                            supportingText = if (mismatch) "Passwords don't match" else null
                        )

                        Spacer(Modifier.height(16.dp))
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            TextButton(onClick = { showReset = true }) {
                                Text("Forgot password?")
                            }
                        }
                    }

                    Button(
                        onClick = { submit() },
                        enabled = !isLoading,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = colors.onPrimary
                            )
                        } else {
                            Text(if (isRegistering) "Create account" else "Log in")
                        }
                    }

                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Banner(message)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isRegistering) "Already have an account?" else "New to SecureChat?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        isRegistering = !isRegistering
                        message = ""
                        confirmPassword = ""
                    }
                ) {
                    Text(if (isRegistering) "Log in" else "Create account")
                }
            }
        }
    }

    if (showReset) {
        ResetPasswordDialog(
            initialEmail = email.trim(),
            authRepository = authRepository,
            onDismiss = { showReset = false }
        )
    }
}

@Composable
private fun ResetPasswordDialog(
    initialEmail: String,
    authRepository: AuthRepository,
    onDismiss: () -> Unit
) {
    var resetEmail by remember { mutableStateOf(initialEmail) }
    var isSending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun send() {
        val clean = resetEmail.trim()
        if (!clean.contains("@")) {
            error = "Enter the email address you registered with."
            return
        }

        isSending = true
        error = ""
        authRepository.sendPasswordReset(clean) { result ->
            isSending = false
            if (result.isSuccess) {
                sent = true
            } else {
                error = AuthRepository.friendlyMessage(result.exceptionOrNull())
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        icon = { Icon(AppIcons.Mail, contentDescription = null) },
        title = { Text(if (sent) "Check your email" else "Reset password") },
        text = {
            if (sent) {
                Text(
                    "If an account exists for ${resetEmail.trim()}, we've sent a link to set a " +
                            "new password. Check your inbox and spam folder.\n\n" +
                            "Your chats stay safe: a new password doesn't affect your encryption keys."
                )
            } else {
                Column {
                    Text("Enter your account email and we'll send you a link to set a new password.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(AppIcons.Mail, contentDescription = null) },
                        singleLine = true,
                        isError = error.isNotEmpty(),
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(onClick = { send() }, enabled = !isSending) {
                    Text(if (isSending) "Sending..." else "Send link")
                }
            }
        },
        dismissButton = {
            if (!sent) {
                TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel") }
            }
        }
    )
}
