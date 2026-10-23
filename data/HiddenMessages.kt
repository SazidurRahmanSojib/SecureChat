package com.example.securechat.data

import android.content.Context

/**
 * "Delete for me": message IDs hidden on this phone only.
 * The other person still has the message.
 */
class HiddenMessages(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("securechat_hidden_messages", Context.MODE_PRIVATE)

    fun load(uid: String, chatId: String): Set<String> =
        prefs.getStringSet(key(uid, chatId), emptySet())?.toSet() ?: emptySet()

    fun hide(uid: String, chatId: String, messageId: String): Set<String> {
        val updated = load(uid, chatId) + messageId
        prefs.edit().putStringSet(key(uid, chatId), updated).apply()
        return updated
    }

    fun clear(uid: String, chatId: String) {
        prefs.edit().remove(key(uid, chatId)).apply()
    }

    private fun key(uid: String, chatId: String) = "$uid|$chatId"
}
