package com.example.securechat.push

import com.example.securechat.data.awaitTask
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Stores this phone's push token in users/{uid}/private/fcm so the server
 * function knows where to send "new message" notifications.
 */
object PushTokens {

    private fun ref(uid: String) =
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("private").document("fcm")

    /** Call after login. Failures are ignored: the app works without notifications. */
    fun register(uid: String) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> save(uid, token) }
        } catch (e: Exception) {
            // Firebase Messaging unavailable on this device
        }
    }

    fun save(uid: String, token: String) {
        ref(uid).set(
            mapOf(
                "tokens" to FieldValue.arrayUnion(token),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
    }

    /** Call before logging out, so this phone stops getting that account's notifications. */
    suspend fun unregister(uid: String) {
        val token = FirebaseMessaging.getInstance().token.awaitTask()
        ref(uid).update(
            mapOf(
                "tokens" to FieldValue.arrayRemove(token),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).awaitTask()
    }
}
