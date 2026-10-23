package com.example.securechat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.securechat.ui.components.AppIcons
import com.example.securechat.ui.components.Banner
import com.example.securechat.ui.components.IconBadge

/** Shown after login while the user's encryption keys are loaded or created. */
@Composable
fun SetupScreen(
    error: String,
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBadge(AppIcons.Lock, filled = false)
        Spacer(Modifier.height(24.dp))

        if (error.isBlank()) {
            Text("Setting up encryption", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Getting your security keys ready...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator()
        } else {
            Text("Setup didn't finish", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Banner(error)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Try again")
            }
            TextButton(onClick = onLogout) {
                Text("Log out")
            }
        }
    }
}
