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

    val period = remember(history, year, month) {
        history.filter { it.date().year == year && (month == null || it.date().month == month) }
    }
    val minutes = remember(period) { period.sumOf { it.ms() } / 60_000 }
    val topArtists = remember(period) {
        period.filter { it.artist().isNotBlank() }.groupBy { it.artist() }
            .map { (name, plays) -> Triple(name, plays.sumOf { it.ms() } / 60_000, plays.first().image) }
            .sortedByDescending { it.second }.take(10)
    }
    val topSongs = remember(period) {
        period.groupBy { it.songId }.map { (_, plays) -> plays.first() to plays.size }
            .sortedByDescending { it.second }.take(10)
    }
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
                            val (name, mins, image) = topArtists[i]
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
                                Column(Modifier.align(Alignment.BottomCenter).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$mins minutes", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                item { ReplayHeader("Your Top Songs") }
                items(topSongs.size) { i ->
                    val (entry, plays) = topSongs[i]
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
                        }
                        Text("$plays ${if (plays == 1) "play" else "plays"}", color = TextMuted, fontSize = 12.sp)
                    }
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
