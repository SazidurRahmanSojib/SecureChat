package com.example.securechat.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.securechat.MainActivity
import com.example.securechat.R

/** The chat currently open on screen, so its notifications can be skipped. */
object ActiveChat {
    @Volatile
    var chatId: String? = null
}

/** Shows notifications. They never include message text or who sent it. */
object Notifications {

    const val CHANNEL_ID = "messages"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New messages and chat requests"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, type: String?, chatId: String?) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (type == "request") "New chat request" else "New encrypted message"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_securechat)
            .setContentTitle("SecureChat")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(notificationId(chatId ?: type ?: "securechat"), notification)
        } catch (e: SecurityException) {
            // Permission was removed while the app was running
        }
    }

    /** Removes the notification for a chat when it is opened. */
    fun cancel(context: Context, chatId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(chatId))
    }

    private fun notificationId(key: String) = key.hashCode()
}
