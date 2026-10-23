package com.music.aalap.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.aalap.R
import com.music.aalap.data.preferences.HistoryEntry
import com.music.aalap.data.preferences.getListeningHistory
import com.music.aalap.ui.share.ShareCards
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val Night = Color(0xFF100B1C)
private val Ember = Color(0xFF7A3A12)
private val SaffronC = Color(0xFFFF8A3D)
private val GoldC = Color(0xFFFFC56B)
private val MutedC = Color(0xFFB8AFC9)

/** Plays logged before 2.0 have no length; count them at a typical song length. */

private data class WrappedData(
    val periodLabel: String,
    val plays: Int,
    val minutes: Long,
    val topArtist: Pair<String, String>?, // name to cover image
    val topSongs: List<Pair<HistoryEntry, Int>>,
    val topArtists: List<Pair<String, Int>>,
    val personality: Pair<String, String>, // title to explanation
    val streakDays: Int,
)

private fun HistoryEntry.date(): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
private fun HistoryEntry.hour(): Int = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).hour
private fun HistoryEntry.artist() = singer.substringBefore(",").trim()

private fun buildWrapped(all: List<HistoryEntry>, month: YearMonth): WrappedData {
    // Counted exactly like History (ListeningStats): real listening time, skips left out.
    val timed = com.music.aalap.data.preferences.ListeningStats.plays(all).filter { YearMonth.from(it.entry.date()) == month }
    val plays = timed.map { it.entry }
    val finished = timed.filter { !it.skipped }.map { it.entry }
    val listenedMs = timed.sumOf { it.listenedMs }
    val artists = finished.filter { it.artist().isNotBlank() }.groupBy { it.artist().lowercase() }
        .map { (_, p) -> (p.firstOrNull { it.artist() != it.artist().uppercase() }?.artist() ?: p.first().artist()) to p }
        .sortedByDescending { it.second.size }
    val hours = plays.groupingBy {
        when (it.hour()) { in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3 }
    }.eachCount()
    val personality = when (hours.maxByOrNull { it.value }?.key) {
        0 -> "Sunrise Listener" to "Most of your music played before noon."
        1 -> "Afternoon Groover" to "Your afternoons had a soundtrack."
        2 -> "Evening Mehfil Host" to "Evenings were when the music came alive."
        3 -> "Night Owl" to "You did your best listening after 10 pm."
        else -> "Fresh Ears" to "Play a few songs to find out."
    }
    // Longest run of consecutive days with at least one play.
    val days = plays.map { it.date() }.toSortedSet().toList()
    var best = if (days.isEmpty()) 0 else 1; var run = best
    for (i in 1 until days.size) {
        run = if (days[i - 1].plusDays(1) == days[i]) run + 1 else 1
        best = maxOf(best, run)
    }
    return WrappedData(
        periodLabel = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
        plays = finished.size,
        minutes = listenedMs / 60_000,
        topArtist = artists.firstOrNull()?.let { (a, p) -> a to p.first().image },
        topSongs = finished.groupBy { "${it.title.lowercase().substringBefore(" (")}|${it.artist().lowercase()}" }
            .map { (_, p) -> p.first() to p.size }.sortedByDescending { it.second }.take(5),
        topArtists = artists.take(5).map { (a, p) -> a to p.size },
        personality = personality,
        streakDays = best,
    )
}

/** "Aalap Wrapped": a month of listening as a story you can share. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun WrappedScreen(navController: NavController) {
    val context = LocalContext.current
    val history = remember { getListeningHistory(context) }
    val thisMonth = remember { YearMonth.now() }
    var month by remember { mutableStateOf(thisMonth) }
    val data = remember(month) { buildWrapped(history, month) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ember, Night, Night)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White,
                    modifier = Modifier.size(26.dp).clickable { navController.navigateUp() },
                )
                Spacer(Modifier.weight(1f))
                listOf(thisMonth.minusMonths(1) to "Last month", thisMonth to "This month").forEach { (m, label) ->
                    val sel = m == month
                    Text(
                        label,
                        color = if (sel) Color(0xFF1A0E05) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) SaffronC else Color.White.copy(alpha = 0.12f))
                            .clickable { month = m }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("YOUR ${data.periodLabel.uppercase()} IN MUSIC", color = GoldC, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)

            if (data.plays == 0) {
                Spacer(Modifier.height(40.dp))
                Text("Nothing yet", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Play some music this month and your Wrapped fills itself in.", color = MutedC, fontSize = 15.sp)
                return@Column
            }

            Text("${data.minutes}", color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Bold, lineHeight = 80.sp)
            Text(
                "minutes · ${data.plays} plays · ${data.streakDays}-day streak",
                color = MutedC, fontSize = 15.sp,
            )

            data.topArtist?.let { (name, image) ->
                WrappedLabel("Top artist")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlideImage(
                        model = image, contentDescription = name, contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier.size(96.dp).clip(CircleShape).border(3.dp, SaffronC, CircleShape),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            WrappedLabel("Top songs")
            data.topSongs.forEachIndexed { i, (entry, plays) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Text("${i + 1}", color = if (i == 0) SaffronC else MutedC, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                    GlideImage(
                        model = entry.image, contentDescription = null, contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(entry.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.singer, color = MutedC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("$plays×", color = MutedC, fontSize = 13.sp)
                }
            }

            WrappedLabel("Top artists")
            data.topArtists.forEachIndexed { i, (name, plays) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("${i + 1}", color = if (i == 0) SaffronC else MutedC, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                    Text(name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(com.music.aalap.ui.components.counted(plays, "play"), color = MutedC, fontSize = 13.sp)
                }
            }

            WrappedLabel("You're a")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(18.dp)
            ) {
                Text(data.personality.first, color = GoldC, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(data.personality.second, color = MutedC, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(SaffronC)
                    .clickable {
                        val card = ShareCards.wrappedCard(
                            context,
                            period = data.periodLabel,
                            minutes = data.minutes,
                            plays = data.plays,
                            topArtist = data.topArtist?.first.orEmpty(),
                            topSongs = data.topSongs.map { it.first.title },
                            topArtists = data.topArtists.map { it.first },
                            personality = data.personality.first,
                        )
                        ShareCards.share(context, card, "aalap-wrapped", "My ${data.periodLabel} on Aalap")
                    }
                    .padding(vertical = 15.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF1A0E05), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share my Wrapped", color = Color(0xFF1A0E05), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.navigationBarsPadding().height(40.dp))
        }
    }
}

@Composable
private fun WrappedLabel(text: String) {
    Text(
        text.uppercase(), color = SaffronC, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 30.dp, bottom = 12.dp),
    )
}
