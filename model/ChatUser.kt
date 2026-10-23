package com.example.securechat.model

data class ChatUser(
    val uid: String,
    val email: String,
    /** Null until this user has logged in to the encrypted version of the app. */
    val publicKey: String? = null,
    /** Optional name the user chose in Settings. */
    val displayName: String? = null
) {
    /** What to show for this person: their name if they set one, otherwise their email. */
    val label: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: email

    val hasDisplayName: Boolean
        get() = !displayName.isNullOrBlank()
}
