package com.example.securechat.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp

/** Delivery state of one of your own messages. */
enum class MessageStatus { SENDING, SENT, READ }

/**
 * One chat bubble: yours on the right in the brand color, theirs on the left.
 * Long-press opens [menu] (copy, delete for me), anchored to the bubble.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    text: String,
    time: String,
    isMine: Boolean,
    failed: Boolean = false,
    status: MessageStatus? = null,
    onLongClick: () -> Unit = {},
    menu: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val container = if (isMine) colors.primary else colors.surfaceContainerHigh
    val content = if (isMine) colors.onPrimary else colors.onSurface

    // The corner nearest the sender's side is less rounded, like a speech tail
    val shape = if (isMine) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                color = container,
                contentColor = content,
                shape = shape,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .combinedClickable(onClick = {}, onLongClick = onLongClick)
            ) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = if (failed) FontStyle.Italic else null
                    )
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            time,
                            style = MaterialTheme.typography.labelSmall,
                            color = content.copy(alpha = 0.7f)
                        )
                        if (status != null) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = when (status) {
                                    MessageStatus.SENDING -> AppIcons.Schedule
                                    MessageStatus.SENT -> AppIcons.Done
                                    MessageStatus.READ -> AppIcons.DoneAll
                                },
                                contentDescription = when (status) {
                                    MessageStatus.SENDING -> "Sending"
                                    MessageStatus.SENT -> "Sent"
                                    MessageStatus.READ -> "Read"
                                },
                                tint = if (status == MessageStatus.READ) {
                                    colors.tertiaryContainer
                                } else {
                                    content.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
            menu()
        }
    }
}

/** Date divider between messages from different days. */
@Composable
fun DayDivider(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
