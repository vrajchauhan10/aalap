package com.music.spotui.ui.components

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.spotui.data.api.Mood
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.recommendation.AalapRadio
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.SaffronDeep
import com.music.spotui.ui.theme.SaffronGold
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import com.music.spotui.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aalap Radio on Home: a big "Your Mix" station made from your taste, then one
 * station per mood, ordered for you and the time of day. Tapping a station builds it
 * and starts playing; the engine keeps it going after it ends.
 */
@Composable
fun AalapRadioSection() {
    val context = LocalContext.current
    val player: PlayerViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf<String?>(null) }

    val artists by produceState(emptyList<String>()) { value = withContext(Dispatchers.IO) { AalapRadio.topArtists(context) } }
    val moods = remember { AalapRadio.moodOrder(context) }

    fun start(key: String, build: suspend () -> List<SongsModel>) {
        if (loading != null) return
        loading = key
        scope.launch {
            val songs = runCatching { withContext(Dispatchers.IO) { build() } }.getOrDefault(emptyList())
            loading = null
            if (songs.isEmpty()) {
                Toast.makeText(context, "Couldn't build this station right now. Try again in a moment.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            player.updateQueue(songs)
            player.playSongAt(songs, 0, context)
        }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp),
        ) {
            Box(Modifier.size(width = 4.dp, height = 20.dp).clip(RoundedCornerShape(2.dp)).background(Saffron))
            Spacer(Modifier.width(10.dp))
            Text("Aalap Radio", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("Made from how you listen", color = TextMuted, fontSize = 11.sp)
        }

        // ── Your Mix ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(178.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(SaffronGold, Saffron, SaffronDeep, Color(0xFF3A1F6B))))
                .clickable {
                    start("mix") {
                        AalapRadio.yourMix(context).ifEmpty { AalapRadio.moodRadio(context, moods.first()) }
                    }
                }
                .padding(18.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("AALAP RADIO", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(2.dp))
                Text("Your Mix", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (artists.isEmpty()) "Your favourites and new finds, mixed for you"
                    else artists.joinToString(", ") + " and more",
                    color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1A0E05)),
                ) {
                    if (loading == "mix") AalapLoader(size = 26.dp)
                    else AalapMark(Modifier.size(30.dp), arcColor = Color.Transparent, playColor = Saffron, playScale = 1.25f, arrowAlpha = 0f)
                }
            }
            AalapMark(Modifier.size(116.dp), arcColor = Color.White.copy(alpha = 0.92f), playColor = Color.White)
        }

        // ── Mood stations ──
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(moods.size) { i ->
                val mood = moods[i]
                MoodStation(mood, loading == mood.name) {
                    start(mood.name) { AalapRadio.moodRadio(context, mood) }
                }
            }
        }
    }
}

@Composable
private fun MoodStation(mood: Mood, loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 120.dp, height = 138.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(mood.colors.first), Color(mood.colors.second))))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        AalapMark(
            Modifier.size(28.dp).align(Alignment.TopStart),
            arcColor = Color.White.copy(alpha = 0.85f), playColor = Color.White,
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AalapLoader(size = 34.dp) }
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(mood.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("Radio", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}
