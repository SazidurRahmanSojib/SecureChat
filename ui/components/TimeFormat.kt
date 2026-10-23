package com.example.securechat.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

// A message that is still sending has no server time yet (Long.MAX_VALUE); treat it as now.
private fun normalize(millis: Long): Long =
    if (millis == Long.MAX_VALUE) System.currentTimeMillis() else millis

fun formatMessageTime(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(normalize(millis)))

fun isSameDay(a: Long, b: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = normalize(a) }
    val second = Calendar.getInstance().apply { timeInMillis = normalize(b) }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

fun formatDayLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    return when {
        isSameDay(millis, now) -> "Today"
        isSameDay(millis, now - DAY_MILLIS) -> "Yesterday"
        else -> SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(normalize(millis)))
    }
}

/** Short time for the chat list: "3:45 PM", "Yesterday", "12 Oct" or "12/10/25". */
fun formatChatListTime(millis: Long): String {
    if (millis <= 0L) return ""
    val now = System.currentTimeMillis()
    val time = normalize(millis)
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val year = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR)

    return when {
        isSameDay(time, now) -> formatMessageTime(time)
        isSameDay(time, now - DAY_MILLIS) -> "Yesterday"
        year == thisYear -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time))
        else -> SimpleDateFormat("d/M/yy", Locale.getDefault()).format(Date(time))
    }
}
