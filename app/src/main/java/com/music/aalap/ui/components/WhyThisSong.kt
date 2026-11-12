package com.music.aalap.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.aalap.data.recommendation.HomeMixes

/** Plain words for each reason tag the engine gives a pick. */
fun reasonExplained(reason: String): String = when (reason) {
    "Your taste" -> "It sounds like what you play most: similar singers, mood and pace."
    "Trending" -> "Lots of people are playing it right now."
    "Top hit" -> "One of the biggest songs in the charts you listen to."
    "Liked" -> "You liked it."
    else -> "Picked for you from what you've been playing."
}

/**
 * Long-press on a pick: why it's there, in plain words, and "Less like this", which is the
 * same feedback as the song menu (remembered for good, takes it out of the queue).
 */
@Composable
fun WhyThisSongDialog(pick: HomeMixes.TodaySong, onLess: () -> Unit, onDismiss: () -> Unit) {
    val reasons = buildList {
        add(pick.reason)
        if (pick.matches && pick.reason != "Your taste") add("Your taste")
    }.distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.music.aalap.ui.theme.Surface,
        title = { Text("Why this song?", color = com.music.aalap.ui.theme.TextPrimary) },
        text = {
            Column {
                Text(
                    "${pick.song.title} · ${pick.song.singer.split(",").first().trim()}",
                    color = com.music.aalap.ui.theme.TextMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                reasons.forEach { r ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("● ", color = reasonColor(r), fontSize = 14.sp)
                        Column {
                            Text(if (r == "For you") "For you" else r, color = reasonColor(r), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(reasonExplained(r), color = com.music.aalap.ui.theme.TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = com.music.aalap.ui.theme.Saffron) }
        },
        dismissButton = {
            TextButton(onClick = { onLess(); onDismiss() }) { Text("Less like this", color = com.music.aalap.ui.theme.TextMuted) }
        },
    )
}
