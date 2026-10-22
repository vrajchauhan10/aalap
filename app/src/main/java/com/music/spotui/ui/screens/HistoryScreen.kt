package com.music.spotui.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.music.spotui.data.preferences.clearHistoryList
import com.music.spotui.data.preferences.getHistoryListClearedAt
import com.music.spotui.data.preferences.getListeningHistory
import com.music.spotui.data.preferences.removeListeningHistory
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.SaffronGold
import com.music.spotui.ui.theme.Surface
import com.music.spotui.ui.theme.SurfaceHigh
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import com.music.spotui.ui.viewmodel.PlayerViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Plays logged before 2.0 have no length; count those at a typical song length. */
private const val TYPICAL_SONG_MS = 210_000L

private fun HistoryEntry.date(): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
private fun HistoryEntry.ms(): Long = if (durationMs > 0) durationMs.toLong() else TYPICAL_SONG_MS
private fun HistoryEntry.artist() = singer.substringBefore(",").trim()
private fun HistoryEntry.toSong() = SongsModel(songId, title, album, singer, image, url, spotifyTrackId, durationMs = durationMs)
private fun HistoryEntry.songKey() = "${title.lowercase().substringBefore(" (")}|${artist().lowercase()}"

/** A play with how long it really lasted: until the next song started, at most the song's length. */
private class Play(val e: HistoryEntry, val listenedMs: Long) {
    /** Under 30 s (or under half a short song) counts as a skip, not a listen. */
    val skipped: Boolean get() = listenedMs < minOf(30_000L, e.ms() / 2)
}

private fun plays(history: List<HistoryEntry>): List<Play> = history.mapIndexed { i, e ->
    val gap = if (i == 0) Long.MAX_VALUE else history[i - 1].ts - e.ts // history is newest first
    Play(e, minOf(e.ms(), gap.coerceAtLeast(0)))
}

/** One ranked row: [move] is places up (+) or down (−) since the previous period, null when new. */
private class Ranked(val key: String, val entry: HistoryEntry, val plays: Int, val minutes: Long, val move: Int?)

private fun rankSongs(period: List<Play>, previous: List<Play>, n: Int): List<Ranked> {
    fun rank(list: List<Play>) = list.filter { !it.skipped }.groupBy { it.e.songKey() }
        .map { (k, ps) -> Ranked(k, ps.first().e, ps.size, ps.sumOf { it.listenedMs } / 60_000, null) }
        .sortedWith(compareByDescending<Ranked> { it.plays }.thenByDescending { it.minutes })
    val before = rank(previous).mapIndexed { i, r -> r.key to i }.toMap()
    return rank(period).take(n).mapIndexed { i, r -> Ranked(r.key, r.entry, r.plays, r.minutes, before[r.key]?.let { it - i }) }
}

private fun rankArtists(period: List<Play>, previous: List<Play>, n: Int): List<Ranked> {
    fun rank(list: List<Play>) = list.filter { !it.skipped && it.e.artist().isNotBlank() }.groupBy { it.e.artist().lowercase() }
        .map { (k, ps) -> Ranked(k, ps.first().e, ps.size, ps.sumOf { it.listenedMs } / 60_000, null) }
        .sortedWith(compareByDescending<Ranked> { it.minutes }.thenByDescending { it.plays })
    val before = rank(previous).mapIndexed { i, r -> r.key to i }.toMap()
    return rank(period).take(n).mapIndexed { i, r -> Ranked(r.key, r.entry, r.plays, r.minutes, before[r.key]?.let { it - i }) }
}

/**
 * Replay: your listening by month — minutes, top artists, top songs — and your recent
 * plays. "Clear all" empties the recent-plays list only; the stats keep every play.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val playerViewModel: PlayerViewModel = hiltViewModel()
    var history by remember { mutableStateOf(getListeningHistory(context)) }
    var clearedAt by remember { mutableStateOf(getHistoryListClearedAt(context)) }
    var confirmClear by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val years = remember(history) { (history.map { it.date().year } + today.year).distinct().sortedDescending() }
    var year by remember { mutableStateOf(today.year) }
    var yearMenu by remember { mutableStateOf(false) }
    // null = the whole year.
    var month by remember { mutableStateOf<Month?>(today.month) }
    val monthsWithPlays = remember(history, year) {
        history.filter { it.date().year == year }.map { it.date().month }.distinct().sorted()
    }

    val allPlays = remember(history) { plays(history) }
    val periodPlays = remember(allPlays, year, month) {
        allPlays.filter { it.e.date().year == year && (month == null || it.e.date().month == month) }
    }
    // The period before, for rank movement (last month, or last year).
    val previousPlays = remember(allPlays, year, month) {
        val m = month
        if (m == null) allPlays.filter { it.e.date().year == year - 1 }
        else {
            val prev = LocalDate.of(year, m, 1).minusMonths(1)
            allPlays.filter { it.e.date().year == prev.year && it.e.date().month == prev.month }
        }
    }
    val period = remember(periodPlays) { periodPlays.map { it.e } }
    val minutes = remember(periodPlays) { periodPlays.sumOf { it.listenedMs } / 60_000 }
    var showAllSongs by remember { mutableStateOf(false) }
    val topArtists = remember(periodPlays, previousPlays) { rankArtists(periodPlays, previousPlays, 10) }
    val topSongsAll = remember(periodPlays, previousPlays) { rankSongs(periodPlays, previousPlays, 30) }
    val topSongs = if (showAllSongs) topSongsAll else topSongsAll.take(10)
    val previousName = month?.let { LocalDate.of(year, it, 1).minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault()) } ?: "${year - 1}"
    val recent = remember(history, clearedAt) { history.filter { it.ts > clearedAt } }
    val periodName = month?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "$year"

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear recent plays?") },
            text = { Text("This empties the list below. Your Replay stats, top artists and recommendations keep all your listening.") },
            confirmButton = {
                TextButton(onClick = {
                    clearHistoryList(context)
                    clearedAt = getHistoryListClearedAt(context)
                    confirmClear = false
                }) { Text("Clear list", color = Saffron) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = TextPrimary) } },
            containerColor = Surface, titleContentColor = TextPrimary, textContentColor = TextMuted,
        )
    }

    // Pulling down from the top closes this page (no refresh, no bounce in between).
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(AppBackground)) {
            // Soft saffron glow in the top-right corner.
            Canvas(Modifier.fillMaxWidth().height(420.dp)) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Saffron.copy(alpha = 0.55f), SaffronGold.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.08f),
                        radius = size.width * 0.75f,
                    )
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 200.dp),
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp, 14.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary,
                            modifier = Modifier.size(26.dp).clickable { navController.navigateUp() })
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Share, "Share your month", tint = TextPrimary,
                            modifier = Modifier.size(22.dp).clickable { navController.navigate(Routes.Wrapped.route) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text("Replay", color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Box {
                            Text(
                                "$year ▾", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
                                    .clickable { yearMenu = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                                years.forEach { y ->
                                    DropdownMenuItem(text = { Text("$y") }, onClick = { year = y; month = null; yearMenu = false })
                                }
                            }
                        }
                    }
                    // Month tabs: the months you listened in, plus the whole year.
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier.padding(top = 18.dp),
                    ) {
                        item { MonthTab("All", month == null) { month = null } }
                        items(monthsWithPlays.size) { i ->
                            val m = monthsWithPlays[i]
                            MonthTab(m.getDisplayName(TextStyle.SHORT, Locale.getDefault()), month == m) { month = m }
                        }
                    }
                    // Headline.
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = TextMuted)) { append("You listened for ") }
                            withStyle(SpanStyle(color = TextPrimary)) { append("%,d minutes".format(minutes)) }
                            withStyle(SpanStyle(color = TextMuted)) { append(" in ") }
                            withStyle(SpanStyle(color = TextPrimary)) { append(periodName) }
                            withStyle(SpanStyle(color = TextMuted)) { append(".") }
                        },
                        fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 8.dp),
                    )
                }

                if (period.isEmpty()) {
                    item {
                        Text("No listening here yet. Play something and it shows up in your Replay.",
                            color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                    }
                } else {
                    item {
                        ReplayHeader("Your Top Artists")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                            items(topArtists.size) { i ->
                                val r = topArtists[i]
                                val name = r.entry.artist()
                                val image = r.entry.image
                                Box(
                                    modifier = Modifier.size(width = 220.dp, height = 290.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceHigh),
                                ) {
                                    GlideImage(
                                        model = image, contentDescription = name, contentScale = ContentScale.Crop,
                                        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
                                    Text("${i + 1}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(16.dp))
                                    MoveBadge(r.move, Modifier.align(Alignment.TopEnd).padding(14.dp))
                                    Column(Modifier.align(Alignment.BottomCenter).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${r.minutes} min · ${r.plays} ${if (r.plays == 1) "play" else "plays"}", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        ReplayHeader("Your Top Songs")
                        Text(
                            "Ranked by plays you finished. Arrows compare with $previousName.",
                            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                        )
                        topSongs.firstOrNull()?.let { top ->
                            TopSongHero(top, periodName) { if (top.entry.url.isNotBlank()) playerViewModel.playWithRadio(top.entry.toSong(), context) }
                        }
                    }
                    val maxPlays = topSongs.firstOrNull()?.plays?.coerceAtLeast(1) ?: 1
                    items((topSongs.size - 1).coerceAtLeast(0)) { j ->
                        val i = j + 1
                        val r = topSongs[i]
                        val entry = r.entry
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { if (entry.url.isNotBlank()) playerViewModel.playWithRadio(entry.toSong(), context) }
                                .padding(horizontal = 20.dp, vertical = 7.dp),
                        ) {
                            Text("${i + 1}", color = if (i < 3) Saffron else TextMuted, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                            GlideImage(
                                model = entry.image, contentDescription = null, contentScale = ContentScale.Crop,
                                loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(entry.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.singer, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // Share of plays compared with your #1.
                                Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceHigh)) {
                                    Box(Modifier.fillMaxWidth(r.plays.toFloat() / maxPlays).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Saffron.copy(alpha = 0.8f)))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                                Text("${r.plays} ${if (r.plays == 1) "play" else "plays"}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${r.minutes} min", color = TextMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            MoveBadge(r.move)
                        }
                    }
                    if (topSongsAll.size > 10) item {
                        Text(
                            if (showAllSongs) "Show top 10" else "Show top ${topSongsAll.size}",
                            color = Saffron, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(50))
                                .clickable { showAllSongs = !showAllSongs }.padding(8.dp),
                        )
                    }
                }

                // Recent plays (clearable without touching the stats above).
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 6.dp)) {
                        Text("Recent plays", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (recent.isNotEmpty()) {
                            Text("Clear all", color = Saffron, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { confirmClear = true }.padding(8.dp))
                        }
                    }
                }
                if (recent.isEmpty()) {
                    item {
                        Text("Nothing new since you cleared the list.", color = TextMuted, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
                recent.groupBy { it.date() }.forEach { (day, entries) ->
                    item {
                        Text(
                            when (day) {
                                today -> "Today"
                                today.minusDays(1) -> "Yesterday"
                                else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
                            },
                            color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(entries.size) { i ->
                        val e = entries[i]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { if (e.url.isNotBlank()) playerViewModel.playWithRadio(e.toSong(), context) }
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                        ) {
                            GlideImage(
                                model = e.image, contentDescription = null, contentScale = ContentScale.Crop,
                                loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(e.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(e.singer, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                Instant.ofEpochMilli(e.ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())),
                                color = TextMuted, fontSize = 11.sp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Default.Close, "Remove from history", tint = TextMuted,
                                modifier = Modifier.size(18.dp).clickable {
                                    removeListeningHistory(context, e)
                                    history = getListeningHistory(context)
                                })
                        }
                    }
                }
            }
        }
    }
}

/** ▲3 / ▼2 / NEW / – next to a ranked row. */
@Composable
private fun MoveBadge(move: Int?, modifier: Modifier = Modifier) {
    val (text, color) = when {
        move == null -> "NEW" to Saffron
        move > 0 -> "▲$move" to Color(0xFF4CC38A)
        move < 0 -> "▼${-move}" to Color(0xFFE57373)
        else -> "–" to TextMuted
    }
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Your #1 song of the period, as a big card. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun TopSongHero(r: Ranked, periodName: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        GlideImage(
            model = r.entry.image, contentDescription = r.entry.title, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.35f)))))
        Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) {
            Text("#1 SONG · ${periodName.uppercase()}", color = SaffronGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(6.dp))
            Text(r.entry.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 28.sp)
            Text(r.entry.singer, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.plays} plays · ${r.minutes} min", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                MoveBadge(r.move)
            }
        }
    }
}

@Composable
private fun MonthTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) TextPrimary else TextMuted,
        fontSize = 17.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

@Composable
private fun ReplayHeader(title: String) {
    Text(
        "$title  ›", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp),
    )
}
