package com.example.securechat.push

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives push messages sent by the Cloud Function. */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.let { PushTokens.save(it.uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
        val chatId = message.data["chatId"]

        // Don't notify about the chat the user is looking at right now
        if (chatId != null && chatId == ActiveChat.chatId) return

        Notifications.show(applicationContext, type, chatId)
    }
}
