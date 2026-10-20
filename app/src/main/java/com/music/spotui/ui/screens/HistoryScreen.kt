package com.music.spotui.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.HistoryEntry
import com.music.spotui.data.preferences.clearListeningHistory
import com.music.spotui.data.preferences.getListeningHistory
import com.music.spotui.data.preferences.removeListeningHistory
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.Hairline
import com.music.spotui.ui.theme.OnSaffron
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.SaffronGold
import com.music.spotui.ui.theme.Surface
import com.music.spotui.ui.theme.SurfaceHigh
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import com.music.spotui.ui.viewmodel.PlayerViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Time window the stats and list are filtered to. */
private enum class HistoryRange(val label: String, val days: Long?) {
    TODAY("Today", 1), WEEK("7 days", 7), MONTH("30 days", 30), ALL("All time", null),
}

private fun HistoryEntry.day(): LocalDate =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

private fun HistoryEntry.toSong() = SongsModel(
    id = songId, title = title, album = album, singer = singer, coverUri = image,
    url = url, spotifyTrackId = spotifyTrackId, durationMs = durationMs,
)

private fun HistoryEntry.mainArtist() = singer.substringBefore(",").trim()

/**
 * Your listening, all stored on the phone: plays / minutes / artists for a chosen
 * period, a 7-day activity chart, top artists and tracks, and every play grouped by
 * day. Tapping a play starts it again (with the rest of that list queued after it).
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val playerViewModel: PlayerViewModel = hiltViewModel()
    var history by remember { mutableStateOf(getListeningHistory(context)) }
    var range by remember { mutableStateOf(HistoryRange.WEEK) }
    var confirmClear by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val inRange = remember(history, range) {
        val days = range.days ?: return@remember history
        val from = today.minusDays(days - 1)
        history.filter { !it.day().isBefore(from) }
    }
    val minutes = remember(inRange) { inRange.sumOf { it.durationMs.toLong() } / 60_000 }
    val artistCount = remember(inRange) { inRange.map { it.mainArtist() }.filter { it.isNotBlank() }.toSet().size }
    val topArtists = remember(inRange) {
        inRange.filter { it.mainArtist().isNotBlank() }
            .groupBy { it.mainArtist() }
            .map { (name, plays) -> Triple(name, plays.size, plays.first().image) }
            .sortedByDescending { it.second }
            .take(8)
    }
    val topTracks = remember(inRange) {
        inRange.groupBy { it.songId }
            .map { (_, plays) -> plays.first() to plays.size }
            .sortedByDescending { it.second }
            .take(5)
    }
    // Plays per day for the last 7 days (oldest first), regardless of the chosen range.
    val week = remember(history) {
        (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to history.count { it.day() == d }
        }
    }
    val byDay = remember(inRange) { inRange.groupBy { it.day() }.toList() }

    // Replays a row and queues the rest of the visible history (unique, replayable) after it.
    fun play(entry: HistoryEntry) {
        if (!entry.canReplay) {
            android.widget.Toast.makeText(context, "This older play can't be replayed", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val queue = inRange.filter { it.canReplay }.distinctBy { it.songId }.map { it.toSong() }
        val index = queue.indexOfFirst { it.id == entry.songId }.coerceAtLeast(0)
        playerViewModel.updateQueue(queue)
        playerViewModel.playSongAt(queue, index, context)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear listening history?") },
            text = { Text("This removes all ${history.size} plays and your stats from this phone. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    clearListeningHistory(context)
                    history = emptyList()
                    confirmClear = false
                }) { Text("Clear", color = Color(0xFFE57373)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = TextPrimary) } },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextMuted,
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 200.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
    ) {
        // ── Top bar ──
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { navController.navigateUp() },
                )
                Spacer(Modifier.width(16.dp))
                Text("Your listening", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    Text(
                        "Clear all",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { confirmClear = true }
                            .padding(6.dp),
                    )
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(32.dp, 80.dp),
                ) {
                    Text("Start listening", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Songs you play show up here, along with your top artists and tracks.",
                        color = TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            return@LazyColumn
        }

        // ── Period chips ──
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            ) {
                HistoryRange.entries.forEach { r ->
                    val sel = r == range
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Saffron else SurfaceHigh)
                            .clickable { range = r }
                            .padding(vertical = 9.dp),
                    ) {
                        Text(r.label, color = if (sel) OnSaffron else TextPrimary, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        // ── Summary ──
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                StatCard("${inRange.size}", "Plays", Modifier.weight(1f))
                StatCard(if (minutes > 0) "$minutes" else "–", "Minutes", Modifier.weight(1f))
                StatCard("$artistCount", "Artists", Modifier.weight(1f))
            }
        }

        // ── 7-day activity ──
        item {
            HistorySection("Last 7 days")
            ActivityChart(week)
        }

        if (inRange.isEmpty()) {
            item {
                Text(
                    "No plays in this period yet.",
                    color = TextMuted, fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp, 24.dp),
                )
            }
            return@LazyColumn
        }

        // ── Top artists ──
        if (topArtists.isNotEmpty()) {
            item {
                HistorySection("Top artists")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(topArtists.size) { i ->
                        val (name, plays, image) = topArtists[i]
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
                            Box(contentAlignment = Alignment.TopStart) {
                                GlideImage(
                                    model = image,
                                    contentScale = ContentScale.Crop,
                                    loading = placeholder(R.drawable.placeholder),
                                    failure = placeholder(R.drawable.placeholder),
                                    contentDescription = name,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (i == 0) Saffron else Hairline, CircleShape),
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (i == 0) Saffron else SurfaceHigh),
                                ) {
                                    Text("${i + 1}", color = if (i == 0) OnSaffron else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$plays ${if (plays == 1) "play" else "plays"}", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // ── Top tracks ──
        if (topTracks.isNotEmpty()) {
            item { HistorySection("Top tracks") }
            val maxPlays = topTracks.first().second
            items(topTracks.size) { i ->
                val (entry, plays) = topTracks[i]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { play(entry) }
                        .padding(16.dp, 6.dp),
                ) {
                    Text("${i + 1}", color = if (i == 0) Saffron else TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    GlideImage(
                        model = entry.image,
                        contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder),
                        failure = placeholder(R.drawable.placeholder),
                        contentDescription = "",
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        Text(entry.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.singer, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        // How this track's plays compare with the #1 track.
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceHigh)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(plays.toFloat() / maxPlays)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Saffron)
                            )
                        }
                    }
                    Text("$plays×", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Every play, grouped by day ──
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            ) {
                Box(Modifier.weight(1f)) { HistorySection("Recent plays") }
                if (inRange.any { it.canReplay }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Saffron)
                            .clickable { inRange.firstOrNull { it.canReplay }?.let { play(it) } }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OnSaffron, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play all", color = OnSaffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        byDay.forEach { (day, entries) ->
            item {
                Text(
                    dayLabel(day, today),
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            items(entries.size) { i ->
                val entry = entries[i]
                HistoryRow(
                    entry = entry,
                    onPlay = { play(entry) },
                    onRemove = {
                        removeListeningHistory(context, entry)
                        history = getListeningHistory(context)
                    },
                )
            }
        }
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
}

@Composable
private fun HistorySection(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
    ) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(Saffron))
        Spacer(Modifier.width(10.dp))
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp),
    ) {
        Text(value, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}

/** Seven saffron bars, one per day; today's bar is full saffron, earlier days softer. */
@Composable
private fun ActivityChart(week: List<Pair<LocalDate, Int>>) {
    val max = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val bar = Saffron
    val soft = SaffronGold.copy(alpha = 0.45f)
    val track = SurfaceHigh
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val slot = size.width / week.size
            val w = slot * 0.5f
            week.forEachIndexed { i, (_, plays) ->
                val x = slot * i + (slot - w) / 2
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                if (plays > 0) {
                    val h = (size.height * plays / max).coerceAtLeast(w)
                    drawRoundRect(
                        if (i == week.lastIndex) bar else soft,
                        Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            week.forEachIndexed { i, (day, plays) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        color = if (i == week.lastIndex) Saffron else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (i == week.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text("$plays", color = TextPrimary, fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HistoryRow(entry: HistoryEntry, onPlay: () -> Unit, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(16.dp, 7.dp),
    ) {
        GlideImage(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
            model = entry.image,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(entry.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.singer, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            Instant.ofEpochMilli(entry.ts).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())),
            color = TextMuted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove from history",
            tint = TextMuted,
            modifier = Modifier
                .size(18.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onRemove() },
        )
    }
}
